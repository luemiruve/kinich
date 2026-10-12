package com.kinich.media;

import java.time.Instant;
import java.util.UUID;

/** Medio para el panel: metadatos del original y sus representaciones (URLs de administración). Sin claves ni rutas. */
public record MediaResponse(UUID id, String contentType, int width, int height, long sizeBytes, Instant createdAt,
                            MediaRenditions renditions) {

    static MediaResponse from(MediaAsset asset, MediaRenditions renditions) {
        return new MediaResponse(asset.getId(), asset.getContentType(), asset.getWidth(), asset.getHeight(),
                asset.getSizeBytes(), asset.getCreatedAt(), renditions);
    }
}
