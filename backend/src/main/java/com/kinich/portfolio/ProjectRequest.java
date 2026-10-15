package com.kinich.portfolio;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Borrador completo de un proyecto; {@code version} solo se usa al actualizar. */
public record ProjectRequest(@Size(max = PortfolioLimits.SLUG_MAX) String slug,
                             @Size(max = PortfolioLimits.TITLE_MAX) String title,
                             @Size(max = PortfolioLimits.EYEBROW_MAX) String eyebrow, UUID categoryId,
                             @Size(max = PortfolioLimits.DESCRIPTION_MAX) String description,
                             @NotNull @Valid CoverRequest cover,
                             @NotNull List<@Valid ProjectImageRequest> images, Long version) {}
