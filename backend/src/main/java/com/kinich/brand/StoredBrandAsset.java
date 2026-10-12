package com.kinich.brand;

import java.io.InputStream;

/** Lo necesario para servir un logotipo: tipo, tamaño, hash (ETag) y contenido. Nunca la ruta de almacenamiento. */
public record StoredBrandAsset(String contentType, long sizeBytes, String sha256, InputStream content) {}
