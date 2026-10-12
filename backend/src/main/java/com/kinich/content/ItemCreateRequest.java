package com.kinich.content;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ItemCreateRequest(@NotNull UUID mediaId, @Size(max = 300) String imageAlt, UUID categoryId,
                                @Size(max = 120) String eyebrow, @Size(max = 200) String title,
                                @Size(max = 2000) String description, @Valid CropInput crop,
                                @Valid FocalPointInput mediaPosition) {}
