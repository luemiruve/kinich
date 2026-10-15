package com.kinich.portfolio;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Publicar un proyecto: la versión del borrador que el cliente vio. */
public record ProjectPublicationRequest(@NotNull @PositiveOrZero Long version) {}
