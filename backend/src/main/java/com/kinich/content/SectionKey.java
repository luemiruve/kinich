package com.kinich.content;

import java.util.Arrays;
import java.util.Optional;

/** Secciones de Inicio y su segmento de URL. */
public enum SectionKey {
    HERO("hero"),
    EDITORIAL_NOTE("editorial-note"),
    DISCIPLINES("disciplines"),
    VALUES("values"),
    FEATURED_SELECTION("featured-selection"),
    CLOSING_CTA("closing-cta");

    private final String slug;

    SectionKey(String slug) {
        this.slug = slug;
    }

    public String slug() {
        return slug;
    }

    public static Optional<SectionKey> fromSlug(String slug) {
        return Arrays.stream(values()).filter(key -> key.slug.equals(slug)).findFirst();
    }
}
