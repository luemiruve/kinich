package com.kinich.content;

import java.util.UUID;

/** Bloque publicado tal como lo ve el público: sin estado, versión, visibilidad ni auditoría. */
public record PublicTextBlock(UUID id, String eyebrow, String title, String body) {
    static PublicTextBlock from(PublishedTextContent copy) {
        return new PublicTextBlock(copy.getId(), copy.getEyebrow(), copy.getTitle(), copy.getBody());
    }
}
