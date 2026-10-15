package com.kinich.portfolio;

import com.kinich.content.CategoryRef;
import com.kinich.content.ImageResponse;
import java.util.UUID;

/** Fila de la lista de proyectos del panel. */
public record AdminProjectSummary(UUID id, String slug, String title, CategoryRef category, ImageResponse cover,
                                  ProjectStatus status, boolean modified, boolean hidden, int position, long version) {}
