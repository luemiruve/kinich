package com.kinich.portfolio;

import java.util.List;

/** Lista en orden de borrador; {@code orderModified}: el orden de los publicados difiere del orden público. */
public record AdminProjectListResponse(List<AdminProjectSummary> projects, boolean orderModified) {}
