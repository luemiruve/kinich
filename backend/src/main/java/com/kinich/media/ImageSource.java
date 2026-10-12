package com.kinich.media;

import java.util.List;

/** Representaciones de un formato, ordenadas por ancho (para srcset). */
public record ImageSource(String type, List<ImageCandidate> candidates) {}
