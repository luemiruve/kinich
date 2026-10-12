package com.kinich.content;

import java.util.UUID;

public record ItemResponse(UUID id, ImageResponse image, UUID mediaId, CategoryRef category, String eyebrow,
                           String title, String description, boolean hidden, int position, long version,
                           ContentStatus status, boolean modified) {}
