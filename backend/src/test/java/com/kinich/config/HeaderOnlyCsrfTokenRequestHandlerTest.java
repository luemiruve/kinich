package com.kinich.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.csrf.DefaultCsrfToken;

class HeaderOnlyCsrfTokenRequestHandlerTest {

    private final HeaderOnlyCsrfTokenRequestHandler handler = new HeaderOnlyCsrfTokenRequestHandler();
    private final DefaultCsrfToken token = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "valor");

    @Test
    void readsTheHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-XSRF-TOKEN", "valor");
        assertThat(handler.resolveCsrfTokenValue(request, token)).isEqualTo("valor");
    }

    @Test
    void neverReadsParametersSoAMultipartBodyIsNotParsed() {
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public String getParameter(String name) {
                throw new AssertionError("no debe leer parámetros (forzaría el análisis del multipart)");
            }
        };
        assertThat(handler.resolveCsrfTokenValue(request, token)).isNull();
    }
}
