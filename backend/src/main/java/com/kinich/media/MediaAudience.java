package com.kinich.media;

import java.util.UUID;

/** Quién consume las URLs: el panel (sesión) o el sitio público (solo lo publicado). Nunca el original. */
public enum MediaAudience {
    ADMIN("/api/admin/media/files/"), PUBLIC("/api/public/media/files/");

    private final String prefix;

    MediaAudience(String prefix) {
        this.prefix = prefix;
    }

    public String url(UUID fileId) {
        return prefix + fileId;
    }
}
