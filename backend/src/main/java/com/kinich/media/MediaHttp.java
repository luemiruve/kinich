package com.kinich.media;

import java.io.IOException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Cabeceras comunes de las respuestas de imagen: tipo fijado por el servidor, CSP con sandbox (una sola cabecera:
 * los writers de Spring Security no sobrescriben una ya presente), inline sin nombre, sin caché compartida.
 * no-cache + ETag = id del archivo servido (los archivos son inmutables), así retirar una imagen sigue siendo
 * inmediato.
 */
final class MediaHttp {

    static final String CSP = "default-src 'none'; frame-ancestors 'none'; sandbox";

    private MediaHttp() {
    }

    static ResponseEntity<?> image(StoredMedia media, String ifNoneMatch) {
        String etag = "\"" + media.id() + "\"";
        if (etag.equals(ifNoneMatch)) {
            closeQuietly(media);
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(CacheControl.noCache()).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.contentType()))
                .contentLength(media.sizeBytes())
                .eTag(etag)
                .cacheControl(CacheControl.noCache())
                .header("Content-Security-Policy", CSP)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(new InputStreamResource(media.content()));
    }

    private static void closeQuietly(StoredMedia media) {
        try {
            media.content().close();
        } catch (IOException ignored) {
            // nada que hacer: el flujo solo se abrió para comprobar que el archivo existe
        }
    }
}
