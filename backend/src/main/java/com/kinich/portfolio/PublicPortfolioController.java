package com.kinich.portfolio;

import com.kinich.shared.error.ApiProblemException;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** API pública de solo lectura del portafolio. JSON siempre con {@code no-store}. */
@RestController
@RequiredArgsConstructor
class PublicPortfolioController {

    private final PublicPortfolioService portfolio;

    @GetMapping("/api/public/pages/portfolio")
    ResponseEntity<PublicPortfolioPageResponse> page() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(portfolio.page());
    }

    /** Sin validar el formato del slug: lo que no existe publicado responde el mismo 404. */
    @GetMapping("/api/public/portfolio/projects/{slug}")
    ResponseEntity<PublicProjectResponse> detail(@PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(portfolio.detail(slug));
    }

    /**
     * El 404 del detalle es idéntico para cualquier slug: sin {@code instance}, que por defecto repite la ruta pedida
     * (y delataría el slug consultado). {@code code} y {@code errors} se copian igual que en el manejador global, por si
     * algún día llega aquí otro error.
     */
    @ExceptionHandler(ApiProblemException.class)
    ResponseEntity<ProblemDetail> notFound(ApiProblemException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        if (ex.getCode() != null) {
            problem.setProperty("code", ex.getCode().name());
        }
        if (!ex.getFieldErrors().isEmpty()) {
            problem.setProperty("errors", ex.getFieldErrors());
        }
        problem.setInstance(URI.create("/api/public/portfolio/projects"));
        return ResponseEntity.status(HttpStatus.valueOf(ex.getStatus().value()))
                .cacheControl(CacheControl.noStore()).body(problem);
    }
}
