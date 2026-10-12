package com.kinich.content;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** El máximo de 500 es un límite técnico del tamaño de la petición, no una regla de negocio. */
public record TextBlockOrderRequest(@NotNull Long revision, @NotNull @Size(max = 500) List<@NotNull UUID> ids) {}
