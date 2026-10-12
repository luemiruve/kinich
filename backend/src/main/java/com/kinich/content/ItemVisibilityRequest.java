package com.kinich.content;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ItemVisibilityRequest(@NotNull Boolean hidden, @NotNull @PositiveOrZero Long version) {}
