package com.kinich.media;

import java.util.List;

/** sources: formatos prioritarios (<source>, hoy AVIF). fallback: formato universal del <img> (JPEG). */
public record MediaRenditions(List<ImageSource> sources, ImageSource fallback) {}
