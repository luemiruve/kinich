package com.kinich.media.image;

/** Imagen validada: formato real, dimensiones y bytes ya saneados (lo único que se almacena). */
public record ImageInspection(MediaFormat format, int width, int height, byte[] content) {}
