package com.kinich.media;

/** Un candidato de srcset: URL de una representación y su ancho real. */
public record ImageCandidate(String url, int width) {}
