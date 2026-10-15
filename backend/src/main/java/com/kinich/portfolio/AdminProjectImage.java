package com.kinich.portfolio;

import com.kinich.content.ImageResponse;
import java.util.UUID;

/** Imagen de la galería en la vista de administración (el alt va dentro de {@code image}). */
public record AdminProjectImage(UUID mediaId, ImageResponse image, String caption) {}
