package com.kinich.content;

import jakarta.validation.constraints.NotNull;

public record TextBlockVisibilityRequest(@NotNull Boolean hidden, @NotNull Long version) {}
