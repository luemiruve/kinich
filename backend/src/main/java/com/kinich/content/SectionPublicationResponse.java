package com.kinich.content;

import java.time.Instant;

public record SectionPublicationResponse(String sectionKey, long publishedRevision, Instant publishedAt) {}
