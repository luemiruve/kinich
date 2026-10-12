package com.kinich.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinich.media.image.ImageFixtures;
import com.kinich.support.ContentTestData;
import com.kinich.support.IntegrationTest;
import com.kinich.user.User;
import com.kinich.user.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Ciclo del token CSRF: se renueva al iniciar y al cerrar sesión, y es estable durante la sesión del navegador
 * (una petición autenticada no lo borra ni lo rota).
 * Antes, cada respuesta autenticada expiraba la cookie XSRF-TOKEN y una escritura que hubiera leído el token
 * justo antes de que terminara un GET concurrente salía sin cookie → 403 (el segundo upload de logotipos).
 * Se emula el navegador con un tarro de cookies que aplica cada Set-Cookie en orden.
 */
@IntegrationTest
class CsrfSessionStabilityIntegrationTest {

    private static final String XSRF = "XSRF-TOKEN";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;

    private final Map<String, String> jar = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        ContentTestData.reset(jdbc);
        users.deleteAll();
        jar.clear();
        users.save(new User("admin@example.com", passwordEncoder.encode("clave-de-prueba-2026"), "Administración"));
    }

    @AfterEach
    void tearDown() {
        ContentTestData.reset(jdbc);
    }

    /** Envía la petición con las cookies del tarro y aplica las Set-Cookie de la respuesta, como un navegador. */
    private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
        Cookie[] cookies = jar.entrySet().stream().map(e -> new Cookie(e.getKey(), e.getValue())).toArray(Cookie[]::new);
        MockHttpServletResponse response = mvc.perform(cookies.length == 0 ? request : request.cookie(cookies))
                .andReturn().getResponse();
        for (Cookie cookie : response.getCookies()) {
            if (cookie.getMaxAge() == 0 || cookie.getValue().isEmpty()) {
                jar.remove(cookie.getName());
            } else {
                jar.put(cookie.getName(), cookie.getValue());
            }
        }
        return response;
    }

    /** Escritura como la hace el SPA: lee el token del tarro y, antes de enviarla, termina un GET concurrente. */
    private MockHttpServletResponse writeWhileAGetCompletes(MockHttpServletRequestBuilder write) throws Exception {
        String header = jar.get(XSRF);
        assertThat(header).as("el SPA tiene un token CSRF antes de escribir").isNotNull();
        assertThat(send(get("/api/admin/media")).getStatus()).isEqualTo(200);
        return send(write.header("X-XSRF-TOKEN", header));
    }

    /** Login real desde el SPA: el token anónimo autoriza la petición y la respuesta trae uno nuevo. */
    private String logIn() throws Exception {
        send(get("/api/auth/me"));
        String anonymous = jar.get(XSRF);
        MockHttpServletResponse login = send(post("/api/auth/login").header("X-XSRF-TOKEN", anonymous)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@example.com\",\"password\":\"clave-de-prueba-2026\"}"));
        assertThat(login.getStatus()).isEqualTo(200);
        assertThat(jar.get(XSRF)).as("el login renueva el token").isNotNull().isNotEqualTo(anonymous);
        return jar.get(XSRF);
    }

    private String upload(byte[] png) throws Exception {
        MockHttpServletResponse response = writeWhileAGetCompletes(multipart("/api/admin/media")
                .file(new MockMultipartFile("file", "logo.png", "image/png", png)));
        assertThat(response.getStatus()).as("upload: %s", response.getContentAsString()).isEqualTo(201);
        return new ObjectMapper().readTree(response.getContentAsString()).get("id").asText();
    }

    @Test
    void consecutiveOperationsIncludingTwoUploadsKeepTheSameCsrfTokenUntilLogout() throws Exception {
        String issued = logIn();
        assertThat(send(get("/api/auth/me")).getStatus()).isEqualTo(200);

        upload(ImageFixtures.transparentPng(48, 16));
        upload(ImageFixtures.transparentPng(48, 16));

        String settings = send(get("/api/admin/settings")).getContentAsString();
        long version = new ObjectMapper().readTree(settings).get("version").asLong();
        MockHttpServletResponse identity = writeWhileAGetCompletes(put("/api/admin/settings/identity")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"businessName\":\"Estudio de prueba\",\"version\":" + version + "}"));
        assertThat(identity.getStatus()).as("identidad: %s", identity.getContentAsString()).isEqualTo(200);

        assertThat(jar.get(XSRF)).as("el token no se rota ni se borra durante la sesión").isEqualTo(issued);

        assertThat(send(post("/api/auth/logout").header("X-XSRF-TOKEN", issued)).getStatus()).isEqualTo(204);
        assertThat(jar).doesNotContainKey("kinich_session");
        assertThat(jar.get(XSRF)).as("el logout renueva el token").isNotNull().isNotEqualTo(issued);
    }

    @Test
    void aStableTokenDoesNotWeakenTheProtection() throws Exception {
        logIn();
        byte[] png = ImageFixtures.transparentPng(8, 8);

        assertThat(send(multipart("/api/admin/media").file(new MockMultipartFile("file", "a.png", "image/png", png)))
                .getStatus()).as("sin cabecera").isEqualTo(403);
        assertThat(send(multipart("/api/admin/media").file(new MockMultipartFile("file", "a.png", "image/png", png))
                .header("X-XSRF-TOKEN", "otro-token")).getStatus()).as("cabecera distinta de la cookie").isEqualTo(403);
    }
}
