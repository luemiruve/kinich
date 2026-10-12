package com.kinich.media.image;

import java.util.Arrays;
import java.util.Optional;

public enum MediaFormat {
    JPEG("image/jpeg"), PNG("image/png"), WEBP("image/webp"), AVIF("image/avif");

    private final String contentType;

    MediaFormat(String contentType) {
        this.contentType = contentType;
    }

    public String contentType() {
        return contentType;
    }

    public static Optional<MediaFormat> fromContentType(String contentType) {
        return Arrays.stream(values()).filter(f -> f.contentType.equals(contentType)).findFirst();
    }
}
