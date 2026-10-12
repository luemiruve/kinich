package com.kinich.content;

import java.util.List;

public record AdminPageResponse(String pageKey, List<AdminSectionResponse> sections) {}
