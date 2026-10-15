package com.kinich.portfolio;

import java.util.List;

/** Detalle público de un proyecto publicado. */
public record PublicProjectResponse(String slug, String title, String eyebrow, String category, String description,
                                    List<PublicProjectImage> images) {}
