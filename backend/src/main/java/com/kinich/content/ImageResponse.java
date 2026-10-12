package com.kinich.content;

import com.kinich.media.ImageSource;
import java.util.List;

/**
 * Imagen de un elemento lista para <picture>: dimensiones del original (proporción), representaciones por formato
 * (srcset) y el encuadre del elemento (crop o mediaPosition). Nunca URLs ni ids del original.
 */
public record ImageResponse(String alt, int width, int height, List<ImageSource> sources, ImageSource fallback,
                            Crop crop, FocalPoint mediaPosition) {}
