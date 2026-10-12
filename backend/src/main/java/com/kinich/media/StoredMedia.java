package com.kinich.media;

import java.io.InputStream;
import java.util.UUID;

/** Lo necesario para servir un archivo (id = identidad HTTP y ETag): tipo fijado por el servidor, tamaño y contenido. Nunca la ruta. */
public record StoredMedia(UUID id, String contentType, long sizeBytes, InputStream content) {}
