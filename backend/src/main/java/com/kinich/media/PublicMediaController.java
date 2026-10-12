package com.kinich.media;

import com.kinich.shared.error.ApiProblemException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadena pública (@Order(1), solo GET, sin cookies). Solo representaciones web de medios de la instantánea publicada;
 * el original, el borrador y todo lo demás responden el mismo 404.
 */
@RestController
@RequestMapping("/api/public/media")
@RequiredArgsConstructor
class PublicMediaController {

    private final MediaService media;

    @GetMapping("/files/{id}")
    ResponseEntity<?> image(@PathVariable String id,
                            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        return MediaHttp.image(media.openPublishedWebFile(parse(id)), ifNoneMatch);
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
}
