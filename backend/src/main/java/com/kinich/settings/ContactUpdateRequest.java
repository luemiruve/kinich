package com.kinich.settings;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Los máximos de @Size son una primera barrera; las reglas completas están en SettingsNormalizer. */
public record ContactUpdateRequest(@Size(max = 1000) String email, @Size(max = 64) String whatsapp,
                                   @Size(max = 64) String phone, @Size(max = 1000) String location,
                                   @Size(max = 2000) String address, @Size(max = 2000) String openingHours,
                                   @NotNull Long version) {}
