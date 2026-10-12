package com.kinich.settings;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Los máximos de @Size son una primera barrera; las reglas completas están en SettingsNormalizer. */
public record IdentityUpdateRequest(@Size(max = 500) String businessName, @Size(max = 1000) String tagline,
                                    @NotNull Long version) {}
