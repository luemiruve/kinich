package com.kinich.portfolio;

import com.kinich.content.ImageResponse;

/** Tarjeta del listado público: portada con su recorte 4:5. Sin ids internos. */
public record PublicProjectCard(String slug, String title, String eyebrow, String category, String description,
                                ImageResponse cover) {}
