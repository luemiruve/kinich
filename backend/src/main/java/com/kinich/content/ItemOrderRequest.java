package com.kinich.content;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Mismo límite técnico de 500 ids que el orden de textos (Selección destacada no tiene máximo). */
public record ItemOrderRequest(@NotNull @PositiveOrZero Long revision, @NotNull @Size(max = 500) List<@NotNull UUID> ids) {}
