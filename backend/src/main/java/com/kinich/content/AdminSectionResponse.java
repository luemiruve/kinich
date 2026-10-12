package com.kinich.content;

import java.time.Instant;
import java.util.List;

public record AdminSectionResponse(String sectionKey, boolean textEditable, Integer maxBlocks, boolean itemEditable,
                                   Integer maxItems, List<String> itemFields, List<String> requiredItemFields,
                                   ItemFraming itemFraming, List<String> headingFields, SectionHeading heading,
                                   long revision, long publishedRevision, boolean hasUnpublishedChanges,
                                   Instant publishedAt, List<TextBlockResponse> texts, List<ItemResponse> items) {}
