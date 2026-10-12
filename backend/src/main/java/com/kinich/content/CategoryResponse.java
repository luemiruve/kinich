package com.kinich.content;

import java.util.UUID;

public record CategoryResponse(UUID id, String label) {

    static CategoryResponse from(ContentCategory category) {
        return new CategoryResponse(category.getId(), category.getLabel());
    }
}
