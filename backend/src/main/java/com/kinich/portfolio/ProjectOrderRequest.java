package com.kinich.portfolio;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Nuevo orden completo del borrador; cada proyecto con la versión que el cliente vio. */
public record ProjectOrderRequest(
        @NotNull @Size(max = PortfolioLimits.MAX_PROJECTS) List<@NotNull @Valid Item> projects) {

    public record Item(@NotNull UUID id, @NotNull Long version) {}
}
