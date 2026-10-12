package com.kinich.content;

import java.util.List;

public record PublicPageResponse(String pageKey, List<PublicSectionResponse> sections) {}
