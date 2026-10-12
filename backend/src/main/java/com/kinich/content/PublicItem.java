package com.kinich.content;

import java.util.UUID;

/** Solo la instantánea publicada; la URL de la imagen es la pública. Sin mediaId ni ids internos de categoría. */
public record PublicItem(UUID id, ImageResponse image, String category, String eyebrow, String title,
                         String description) {}
