package com.kinich.support;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Ayudas para pruebas de API: sesión real (cookie JWT) y CSRF como lo envía el SPA. */
public final class ApiRequests {

    private ApiRequests() {
    }

    public static RequestPostProcessor session(String token) {
        return request -> {
            addCookie(request, new Cookie("kinich_session", token));
            return request;
        };
    }

    public static RequestPostProcessor csrf() {
        String token = UUID.randomUUID().toString();
        return request -> {
            addCookie(request, new Cookie("XSRF-TOKEN", token));
            request.addHeader("X-XSRF-TOKEN", token);
            return request;
        };
    }

    private static void addCookie(org.springframework.mock.web.MockHttpServletRequest request, Cookie cookie) {
        Cookie[] existing = request.getCookies() == null ? new Cookie[0] : request.getCookies();
        Cookie[] cookies = Arrays.copyOf(existing, existing.length + 1);
        cookies[existing.length] = cookie;
        request.setCookies(cookies);
    }
}
