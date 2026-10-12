package com.kinich.media.processing;

import com.kinich.media.image.MediaFormat;

/** Una representación web generada: formato, dimensiones reales y bytes (sin metadatos). */
public record RenditionFile(MediaFormat format, int width, int height, byte[] content) {}
