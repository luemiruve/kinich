package com.kinich.portfolio;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ProjectVisibilityRequest(@NotNull Boolean hidden, @NotNull @PositiveOrZero Long version) {}
