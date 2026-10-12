package com.kinich.config;

import com.kinich.auth.AuthCookies;
import com.kinich.auth.UserJwtAuthenticationConverter;
import com.kinich.shared.error.ProblemDetailSecurityHandler;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.WebUtils;

@Configuration
@EnableWebSecurity
class SecurityConfig {

    private static final int MIN_SECRET_BYTES = 32;

    /** Rutas que no requieren sesión. En ellas se ignora la cookie para que un token caducado no bloquee el login. */
    private static final RequestMatcher PUBLIC_ENDPOINTS = new OrRequestMatcher(
            new AntPathRequestMatcher("/api/auth/login", HttpMethod.POST.name()),
            new AntPathRequestMatcher("/api/auth/logout", HttpMethod.POST.name()));

    private static final String PUBLIC_API = "/api/public/**";

    /**
     * API pública (solo lectura del contenido publicado). Va antes que la cadena principal: sin JWT, sin CSRF,
     * sin CsrfCookieFilter ni sesión, así que nunca emite Set-Cookie. Cualquier método distinto de GET → 405.
     */
    @Bean
    @Order(1)
    SecurityFilterChain publicApiFilterChain(HttpSecurity http, ProblemDetailSecurityHandler problemHandler)
            throws Exception {
        return http
                .securityMatcher(PUBLIC_API)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, PUBLIC_API).permitAll()
                        .anyRequest().denyAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemHandler.methodNotAllowed())
                        .accessDeniedHandler(problemHandler))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http, BearerTokenResolver bearerTokenResolver,
                                            UserJwtAuthenticationConverter jwtConverter,
                                            ProblemDetailSecurityHandler problemHandler,
                                            CsrfTokenRepository csrfRepository) throws Exception {
        return http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new HeaderOnlyCsrfTokenRequestHandler())
                        // Sin sesión de servidor, cada petición autenticada por la cookie JWT cuenta como «nueva
                        // autenticación» y la estrategia por defecto expiraba la cookie XSRF-TOKEN en cada respuesta:
                        // una escritura que leyera el token mientras terminaba otra petición salía sin cookie → 403.
                        // El token se mantiene estable durante la sesión del navegador.
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy())
                        .withObjectPostProcessor(new ObjectPostProcessor<CsrfFilter>() {
                            @Override
                            public <O extends CsrfFilter> O postProcess(O filter) {
                                // El resource server exime de CSRF a quien tenga bearer; aquí el bearer es una cookie de sesión.
                                filter.setRequireCsrfProtectionMatcher(CsrfFilter.DEFAULT_CSRF_MATCHER);
                                return filter;
                            }
                        }))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(bearerTokenResolver)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .build();
    }

    /**
     * Token CSRF en cookie legible por el SPA, que lo devuelve en la cabecera X-XSRF-TOKEN. Es estable durante la
     * sesión del navegador y solo se renueva al cambiar de identidad (login y logout, en AuthController).
     */
    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Strict"));
        return repository;
    }

    /** El JWT solo se acepta desde la cookie HttpOnly, nunca desde parámetros de URL. */
    @Bean
    BearerTokenResolver bearerTokenResolver(AuthCookies authCookies) {
        return request -> {
            if (PUBLIC_ENDPOINTS.matches(request)) {
                return null;
            }
            var cookie = WebUtils.getCookie(request, authCookies.name());
            return cookie == null || cookie.getValue().isBlank() ? null : cookie.getValue();
        };
    }

    @Bean
    SecretKey jwtSecretKey(KinichProperties properties) {
        byte[] bytes = Base64.getDecoder().decode(properties.jwt().secret());
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("KINICH_JWT_SECRET debe tener al menos 256 bits en Base64");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey, KinichProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.jwt().issuer())));
        return decoder;
    }

    /** BCrypt por defecto, con prefijo de algoritmo para poder migrar a otro sin invalidar contraseñas. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** Solo los orígenes configurados explícitamente; sin configuración se rechaza toda petición de otro origen. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(KinichProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.corsAllowedOrigins());
        config.addAllowedMethod(HttpMethod.GET);
        config.addAllowedMethod(HttpMethod.POST);
        config.addAllowedMethod(HttpMethod.PUT);
        config.addAllowedMethod(HttpMethod.PATCH);
        config.addAllowedMethod(HttpMethod.DELETE);
        config.addAllowedHeader("Content-Type");
        config.addAllowedHeader("X-XSRF-TOKEN");
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
