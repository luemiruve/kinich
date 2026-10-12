package com.kinich.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Resuelve el token CSRF SOLO desde la cabecera. El handler por defecto cae a request.getParameter("_csrf"),
 * que en una petición multipart obliga a leer el cuerpo entero (hasta 10 MB) antes de rechazarla, incluso a un
 * anónimo. El SPA siempre envía la cabecera X-XSRF-TOKEN.
 */
final class HeaderOnlyCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        return request.getHeader(csrfToken.getHeaderName());
    }
}
