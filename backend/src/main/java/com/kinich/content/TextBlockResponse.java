package com.kinich.content;

import java.util.UUID;

public record TextBlockResponse(UUID id, String eyebrow, String title, String body, boolean hidden, int position,
                                long version, ContentStatus status, boolean modified) {}
