package com.kinich.settings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Nuevo orden completo de la colección; cada enlace con la versión que el cliente vio. */
public record SocialLinkOrderRequest(@NotNull @Size(max = SocialLinkService.MAX_LINKS) List<@NotNull @Valid Item> items) {

    public record Item(@NotNull UUID id, @NotNull Long version) {}
}
