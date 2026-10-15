package com.kinich.portfolio;

import com.kinich.content.Crop;
import java.util.List;
import java.util.UUID;

/** Proyecto ya normalizado; la existencia de medios y categoría y la proporción del recorte las valida el servicio. */
public record ProjectValues(String slug, String title, String eyebrow, UUID categoryId, String description,
                            UUID coverMediaId, String coverAlt, Crop coverCrop, List<ProjectImageValues> images) {}
