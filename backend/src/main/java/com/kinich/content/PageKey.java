package com.kinich.content;

/** Páginas con contenido administrable. En PostgreSQL es VARCHAR + CHECK (ver V2). */
public enum PageKey {
    HOME;

    public String slug() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
