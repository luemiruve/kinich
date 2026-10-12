package com.kinich.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kinich.support.IntegrationTest;
import com.kinich.user.User;
import com.kinich.user.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Con el limitador lleno, una clave nueva recibe 429 sin cookie de sesión; las ya registradas siguen
 * funcionando. Usa un contexto propio con un tope de 4 claves (dos intentos de correo + IP distintos).
 */
@IntegrationTest
@TestPropertySource(properties = "kinich.login-rate-limit.max-tracked-keys=4")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@ExtendWith(OutputCaptureExtension.class)
class LoginRateLimiterSaturationIntegrationTest {

    private static final String PASSWORD = "clave-de-prueba-2026";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.save(new User("admin@example.com", passwordEncoder.encode(PASSWORD), "Administración"));
    }

    @Test
    void newKeyIsRejectedWith429AndNoSessionCookieWhenFull() throws Exception {
        mvc.perform(login("admin@example.com", "incorrecta", "10.0.0.1")).andExpect(status().isUnauthorized());
        mvc.perform(login("otro@example.com", "incorrecta", "10.0.0.2")).andExpect(status().isUnauthorized());

        mvc.perform(login("admin@example.com", PASSWORD, "10.0.0.3"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void trackedKeysCanStillLogInWhenFull() throws Exception {
        mvc.perform(login("admin@example.com", "incorrecta", "10.0.0.1")).andExpect(status().isUnauthorized());
        mvc.perform(login("otro@example.com", "incorrecta", "10.0.0.2")).andExpect(status().isUnauthorized());

        mvc.perform(login("admin@example.com", PASSWORD, "10.0.0.1"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("kinich_session"));
    }

    @Test
    void saturationLogsASingleWarningInsteadOfOnePerRequest(CapturedOutput output) throws Exception {
        mvc.perform(login("admin@example.com", "incorrecta", "10.0.0.1")).andExpect(status().isUnauthorized());
        mvc.perform(login("otro@example.com", "incorrecta", "10.0.0.2")).andExpect(status().isUnauthorized());

        for (int i = 0; i < 3; i++) {
            mvc.perform(login("nuevo" + i + "@example.com", "incorrecta", "10.0.1." + i))
                    .andExpect(status().isTooManyRequests());
        }

        assertThat(output.getOut()).containsOnlyOnce("Limitador de inicio de sesión saturado")
                .doesNotContain("bloqueado por exceso de intentos");
    }

    private static MockHttpServletRequestBuilder login(String email, String password, String ip) {
        String token = UUID.randomUUID().toString();
        return post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .cookie(new Cookie("XSRF-TOKEN", token))
                .header("X-XSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }
}
