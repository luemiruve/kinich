package com.kinich.portfolio;

import com.kinich.content.CategoryRef;
import com.kinich.content.ImageResponse;
import java.util.List;
import java.util.UUID;

/** Borrador completo de un proyecto con su estado derivado; {@code publishedSlug} es el de la instantánea, si existe. */
public record AdminProjectResponse(UUID id, String slug, String publishedSlug, String title, String eyebrow,
                                   CategoryRef category, String description, ImageResponse cover, UUID coverMediaId,
                                   List<AdminProjectImage> images, ProjectStatus status, boolean modified,
                                   boolean hidden, int position, long version) {}
