package com.kinich.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TextBlockUpdateRequest(@Size(max = 120) String eyebrow, @Size(max = 200) String title,
                                     @NotBlank @Size(max = 2000) String body, @NotNull Long version) {}
