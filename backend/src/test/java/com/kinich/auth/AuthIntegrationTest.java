package com.kinich.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kinich.support.IntegrationTest;
import com.kinich.user.User;
import com.kinich.user.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@IntegrationTest
class AuthIntegrationTest {

    private static final String PASSWORD = "clave-de-prueba-2026";
    private static final String COOKIE = "kinich_session";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private LoginRateLimiter rateLimiter;

    private User user;
    /** IP distinta por prueba para que el limitador de intentos no se comparta entre pruebas. */
    private String ip;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        rateLimiter.reset("admin@example.com");
        user = userRepository.save(new User("Admin@Example.com", passwordEncoder.encode(PASSWORD), "Administración"));
        ip = "10.0." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
    }

    @Test
    void loginSetsHttpOnlyCookieAndNeverReturnsTheToken() throws Exception {
        mvc.perform(login("admin@example.com", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@example.com"))
                .andExpect(jsonPath("$.fullName").value("Administración"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(result -> assertThat(sessionSetCookie(result))
                        .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api"));
    }

    @Test
    void emailIsCaseInsensitive() throws Exception {
        mvc.perform(login("ADMIN@example.COM", PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameAnswer() throws Exception {
        String wrongPassword = mvc.perform(login("admin@example.com", "otra-contrasena"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = mvc.perform(login("nadie@example.com", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).isEqualTo(unknownEmail).contains("Correo o contraseña incorrectos.");
    }

    @Test
    void disabledUserCannotLogIn() throws Exception {
        user.disable();
        userRepository.save(user);

        mvc.perform(login("admin@example.com", PASSWORD)).andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").with(remoteAddr())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("admin@example.com", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void csrfCookieIssuedByTheApiAllowsLogin() throws Exception {
        Cookie xsrf = mvc.perform(get("/api/auth/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();

        mvc.perform(post("/api/auth/login").with(remoteAddr())
                        .cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("admin@example.com", PASSWORD)))
                .andExpect(status().isOk());
    }

    /**
     * El token CSRF se renueva al cambiar de identidad (login y logout) y no en cada petición: el token anterior
     * al login no sigue sirviendo a la sesión autenticada.
     */
    @Test
    void loginRenewsTheCsrfTokenAndTheNewOneAuthorizesTheNextWrite() throws Exception {
        Cookie before = mvc.perform(get("/api/auth/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");

        MvcResult result = mvc.perform(post("/api/auth/login").with(remoteAddr()).cookie(before)
                        .header("X-XSRF-TOKEN", before.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(body("admin@example.com", PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        Cookie renewed = result.getResponse().getCookie("XSRF-TOKEN");
        Cookie session = result.getResponse().getCookie(COOKIE);

        assertThat(renewed).isNotNull();
        assertThat(renewed.getValue()).isNotBlank().isNotEqualTo(before.getValue());
        assertThat(renewed.getMaxAge()).isNotZero();
        assertThat(renewed.getPath()).isEqualTo("/");
        mvc.perform(post("/api/auth/logout").cookie(session, renewed).header("X-XSRF-TOKEN", renewed.getValue()))
                .andExpect(status().isNoContent());
    }

    @Test
    void logoutRenewsTheCsrfToken() throws Exception {
        Cookie before = new Cookie("XSRF-TOKEN", UUID.randomUUID().toString());

        Cookie renewed = mvc.perform(post("/api/auth/logout").cookie(before).header("X-XSRF-TOKEN", before.getValue()))
                .andExpect(status().isNoContent()).andReturn().getResponse().getCookie("XSRF-TOKEN");

        assertThat(renewed).isNotNull();
        assertThat(renewed.getValue()).isNotBlank().isNotEqualTo(before.getValue());
    }

    @Test
    void aFailedLoginDoesNotRenewTheCsrfToken() throws Exception {
        mvc.perform(login("admin@example.com", "incorrecta-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertThat(result.getResponse().getCookie("XSRF-TOKEN")).isNull());
    }

    /** La cookie de sesión no exime de CSRF: el resource server lo haría si no se fijara el matcher por defecto. */
    @Test
    void authenticatedWriteWithoutCsrfIsRejected() throws Exception {
        Cookie session = loginAndGetCookie();

        mvc.perform(post("/api/auth/me").cookie(session))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/me").cookie(session, new Cookie("XSRF-TOKEN", "uno"))
                        .header("X-XSRF-TOKEN", "otro"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/me").cookie(session).with(csrf()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
    }

    @Test
    void invalidPayloadReturnsFieldErrorsWithoutEchoingValues() throws Exception {
        mvc.perform(login("no-es-un-correo", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(content().string(not(containsString("no-es-un-correo"))));
    }

    @Test
    void malformedJsonDoesNotLeakParserDetails() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).with(remoteAddr())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(content().string(not(containsString("JSON parse"))))
                .andExpect(content().string(not(containsString("com.fasterxml"))));
    }

    @Test
    void tooManyFailuresBlockTheEmailEvenWithTheRightPassword() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(login("admin@example.com", "incorrecta-" + i)).andExpect(status().isUnauthorized());
        }

        mvc.perform(login("admin@example.com", PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void passwordsLongerThan72BytesCountAsFailedAttempts() throws Exception {
        String tooLong = "x".repeat(100);
        for (int i = 0; i < 5; i++) {
            mvc.perform(login("admin@example.com", tooLong)).andExpect(status().isUnauthorized());
        }

        mvc.perform(login("admin@example.com", PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void successfulLoginResetsTheEmailCounter() throws Exception {
        for (int i = 0; i < 4; i++) {
            mvc.perform(login("admin@example.com", "incorrecta-" + i)).andExpect(status().isUnauthorized());
        }
        mvc.perform(login("admin@example.com", PASSWORD)).andExpect(status().isOk());

        for (int i = 0; i < 5; i++) {
            mvc.perform(login("admin@example.com", "incorrecta-" + i)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Debes iniciar sesión para continuar."));
    }

    @Test
    void meReturnsTheSessionUser() throws Exception {
        Cookie session = loginAndGetCookie();

        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@example.com"));
    }

    @Test
    void tokenInAuthorizationHeaderIsIgnored() throws Exception {
        String token = tokenService.issue(user.getId());

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = tokenService.issue(user.getId());
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        mvc.perform(get("/api/auth/me").cookie(new Cookie(COOKIE, tampered))).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        Instant past = Instant.now().minus(2, ChronoUnit.DAYS);
        String expired = sign(JwtClaimsSet.builder().issuer("kinich").subject(user.getId().toString())
                .issuedAt(past).expiresAt(past.plus(1, ChronoUnit.HOURS)).build());

        mvc.perform(get("/api/auth/me").cookie(new Cookie(COOKIE, expired))).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() throws Exception {
        String foreign = sign(JwtClaimsSet.builder().issuer("otro").subject(user.getId().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plus(1, ChronoUnit.HOURS)).build());

        mvc.perform(get("/api/auth/me").cookie(new Cookie(COOKIE, foreign))).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenOfDisabledOrUnknownUserIsRejected() throws Exception {
        Cookie session = loginAndGetCookie();
        user.disable();
        userRepository.save(user);

        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(new Cookie(COOKIE, tokenService.issue(UUID.randomUUID()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staleCookieDoesNotBlockANewLogin() throws Exception {
        mvc.perform(login("admin@example.com", PASSWORD).cookie(new Cookie(COOKIE, "basura")))
                .andExpect(status().isOk());
    }

    @Test
    void logoutExpiresTheCookie() throws Exception {
        mvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(sessionSetCookie(result)).startsWith(COOKIE + "=;").contains("Max-Age=0"));
    }

    @Test
    void requestsFromOriginsNotAllowedAreRejected() throws Exception {
        mvc.perform(login("admin@example.com", PASSWORD).header(HttpHeaders.ORIGIN, "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void responsesCarrySecurityHeaders() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
    }

    @Test
    void auditColumnsAreFilled() {
        User stored = userRepository.findById(user.getId()).orElseThrow();

        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.getUpdatedAt()).isNotNull();
        assertThat(stored.getEmail()).isEqualTo("admin@example.com");
    }

    /** La respuesta de login/logout también renueva XSRF-TOKEN: se examina solo la cabecera de la cookie de sesión. */
    private static String sessionSetCookie(MvcResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(value -> value.startsWith(COOKIE + "=")).findFirst()
                .orElseThrow(() -> new AssertionError("sin Set-Cookie de la sesión"));
    }

    private Cookie loginAndGetCookie() throws Exception {
        MvcResult result = mvc.perform(login("admin@example.com", PASSWORD)).andExpect(status().isOk()).andReturn();
        return result.getResponse().getCookie(COOKIE);
    }

    private MockHttpServletRequestBuilder login(String email, String password) {
        return post("/api/auth/login").with(csrf()).with(remoteAddr())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(email, password));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor remoteAddr() {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    /**
     * Reproduce el flujo real del SPA: cookie XSRF-TOKEN más la misma cabecera. No se usa el csrf() de
     * spring-security-test porque sustituye el repositorio del filtro en todo el contexto compartido.
     */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor csrf() {
        String token = UUID.randomUUID().toString();
        return request -> {
            Cookie[] existing = request.getCookies() == null ? new Cookie[0] : request.getCookies();
            Cookie[] cookies = java.util.Arrays.copyOf(existing, existing.length + 1);
            cookies[existing.length] = new Cookie("XSRF-TOKEN", token);
            request.setCookies(cookies);
            request.addHeader("X-XSRF-TOKEN", token);
            return request;
        };
    }

    private String sign(JwtClaimsSet claims) {
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static String body(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }
}
