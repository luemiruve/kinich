package com.kinich.settings;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SocialLinkCreateRequest(@NotNull SocialPlatform platform, @Size(max = 500) String label,
                                      @Size(max = 4096) String url, @NotNull Boolean visible) {}
