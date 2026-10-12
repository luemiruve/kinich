package com.kinich.content;

import jakarta.validation.constraints.NotNull;

public record SectionPublicationRequest(@NotNull Long revision) {}
