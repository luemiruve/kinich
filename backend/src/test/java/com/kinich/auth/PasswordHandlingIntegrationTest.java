package com.kinich.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.kinich.support.IntegrationTest;
import com.kinich.user.User;
import com.kinich.user.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Garantías sobre la contraseña: solo se guarda como hash BCrypt y nunca aparece en respuestas ni en logs.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class PasswordHandlingIntegrationTest {

    private static final String PASSWORD = "contrasena-de-prueba-" + UUID.randomUUID();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private LoginRateLimiter rateLimiter;

    /** IP distinta por prueba: los intentos también cuentan para la IP en el limitador compartido. */
    private String ip;

    @BeforeEach
    void setUp() {
        ip = "10.1." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
        userRepository.deleteAll();
        rateLimiter.reset("admin@example.com");
        userRepository.save(new User("admin@example.com", passwordEncoder.encode(PASSWORD), "Administración"));
    }

    @Test
    void passwordIsStoredOnlyAsBcryptHash() {
        String stored = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class,
                "admin@example.com");

        assertThat(stored).startsWith("{bcrypt}$2").doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, stored)).isTrue();
        // Ninguna otra columna de la tabla contiene la contraseña.
        Integer plainCopies = jdbc.queryForObject(
                "SELECT count(*) FROM users u WHERE position(? in CAST(row_to_json(u) AS text)) > 0",
                Integer.class, PASSWORD);
        assertThat(plainCopies).isZero();
    }

    @Test
    void passwordNeverAppearsInResponsesOrLogs(CapturedOutput output) throws Exception {
        String ok = body(login(PASSWORD));
        String wrong = body(login(PASSWORD + "-incorrecta"));
        String invalid = body(mvc.perform(post("/api/auth/login").with(csrf()).with(remoteAddr())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"no-es-correo\",\"password\":\"" + PASSWORD + "\"}")).andReturn());
        String tooLong = body(login(PASSWORD + "x".repeat(200)));

        assertThat(ok).doesNotContain(PASSWORD).doesNotContainIgnoringCase("password").doesNotContain("$2");
        assertThat(wrong).doesNotContain(PASSWORD);
        assertThat(invalid).doesNotContain(PASSWORD);
        assertThat(tooLong).doesNotContain(PASSWORD);
        assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain("$2a$").doesNotContain("$2b$");
    }

    @Test
    void sessionEndpointNeverExposesTheHash() throws Exception {
        Cookie session = login(PASSWORD).getResponse().getCookie("kinich_session");

        String me = mvc.perform(get("/api/auth/me").cookie(session)).andReturn().getResponse().getContentAsString();

        assertThat(me).doesNotContainIgnoringCase("password").doesNotContain("$2").doesNotContain("bcrypt");
    }

    private MvcResult login(String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).with(remoteAddr())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@example.com\",\"password\":\"" + password + "\"}")).andReturn();
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString() + result.getResponse().getHeaderNames().stream()
                .map(name -> result.getResponse().getHeaders(name).toString()).reduce("", String::concat);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor csrf() {
        String token = UUID.randomUUID().toString();
        return request -> {
            request.setCookies(new Cookie("XSRF-TOKEN", token));
            request.addHeader("X-XSRF-TOKEN", token);
            return request;
        };
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor remoteAddr() {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
