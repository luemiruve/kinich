package com.kinich.portfolio;

import com.kinich.content.ImageResponse;

/** Imagen de galería: sin recorte ni punto focal (se muestra entera). */
public record PublicProjectImage(ImageResponse image, String caption) {}
