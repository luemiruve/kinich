package com.kinich.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequest(@NotBlank @Size(max = 60) String label) {}
