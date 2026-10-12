package com.kinich.brand;

import com.kinich.shared.error.ApiProblemException;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Logotipos de la instalación (cadena pública: solo GET, sin cookies). Solo se sirve un recurso referenciado ahora por
 * la configuración. Cada reemplazo crea un recurso con otro id, así que la respuesta es inmutable (ETag = SHA-256).
 * Se sirve como imagen: el SVG se usa en &lt;img&gt;/&lt;source&gt;, nunca insertado en el HTML.
 */
@RestController
@RequestMapping("/api/public/brand-assets")
@RequiredArgsConstructor
class PublicBrandAssetController {

    /** CSP restrictiva con sandbox; solo admite el CSS interno que el sanitizador ya validó. */
    static final String CSP = "default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'; sandbox";
    private static final CacheControl IMMUTABLE = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

    private final BrandAssetService brandAssets;

    @GetMapping("/{id}")
    ResponseEntity<?> logo(@PathVariable String id,
                           @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        StoredBrandAsset asset = brandAssets.openReferenced(parse(id));
        String etag = "\"" + asset.sha256() + "\"";
        if (etag.equals(ifNoneMatch)) {
            closeQuietly(asset);
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(IMMUTABLE).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.contentType()))
                .contentLength(asset.sizeBytes())
                .eTag(etag)
                .cacheControl(IMMUTABLE)
                .header("Content-Security-Policy", CSP)
                .header("Cross-Origin-Resource-Policy", "same-origin")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(new InputStreamResource(asset.content()));
    }

    /** Un id mal formado responde igual que uno inexistente (no 400): no revela nada. */
    private static UUID parse(String id) {
        try {
            UUID uuid = UUID.fromString(id);
            if (!uuid.toString().equals(id)) {
                throw ApiProblemException.notFound();
            }
            return uuid;
        } catch (IllegalArgumentException e) {
            throw ApiProblemException.notFound();
        }
    }

    private static void closeQuietly(StoredBrandAsset asset) {
        try {
            asset.content().close();
        } catch (IOException ignored) {
            // nada que hacer: el flujo solo se abrió para comprobar que el archivo existe
        }
    }
}
