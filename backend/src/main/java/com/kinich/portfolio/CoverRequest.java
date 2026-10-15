package com.kinich.portfolio;

import com.kinich.content.CropInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Portada del proyecto: medio, texto alternativo y recorte 4:5 opcional. */
public record CoverRequest(@NotNull UUID mediaId, @Size(max = PortfolioLimits.ALT_MAX) String alt,
                           @Valid CropInput crop) {}
