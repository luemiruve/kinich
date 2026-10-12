package com.kinich.content;

import java.util.UUID;

/** mediaId = id del MediaAsset. framing se resuelve contra las dimensiones del original (ItemFraming). */
public record ItemValues(UUID mediaId, String imageAlt, UUID categoryId, String eyebrow, String title,
                         String description, Framing framing) {

    ItemValues withFraming(Framing resolved) {
        return new ItemValues(mediaId, imageAlt, categoryId, eyebrow, title, description, resolved);
    }
}
