package com.kinich.content;

import java.util.Arrays;
import java.util.Optional;

/** Páginas con contenido administrable. En PostgreSQL es VARCHAR + CHECK (ver V2 y V11). */
public enum PageKey {
    HOME,
    PORTFOLIO;

    public String slug() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Segmento de URL → página; cualquier otro valor (también en mayúsculas) no existe. */
    public static Optional<PageKey> fromSlug(String slug) {
        return Arrays.stream(values()).filter(key -> key.slug().equals(slug)).findFirst();
    }
}
