package com.kinich.auth;

import com.kinich.config.KinichProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie de sesión que transporta el JWT. HttpOnly para que JavaScript no pueda leerla.
 */
@Component
@RequiredArgsConstructor
public class AuthCookies {

    static final String PATH = "/api";

    private final KinichProperties properties;

    public ResponseCookie session(String token) {
        return base(token).maxAge(properties.jwt().ttl()).build();
    }

    public ResponseCookie cleared() {
        return base("").maxAge(Duration.ZERO).build();
    }

    public String name() {
        return properties.cookie().name();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(properties.cookie().name(), value)
                .httpOnly(true)
                .secure(properties.cookie().secure())
                .sameSite("Strict")
                .path(PATH);
    }
}
