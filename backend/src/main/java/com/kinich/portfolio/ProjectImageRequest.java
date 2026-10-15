package com.kinich.portfolio;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Imagen de la galería de un proyecto. */
public record ProjectImageRequest(@NotNull UUID mediaId, @Size(max = PortfolioLimits.ALT_MAX) String alt,
                                  @Size(max = PortfolioLimits.CAPTION_MAX) String caption) {}
