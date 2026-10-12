package com.kinich.content;

import java.util.List;

/** heading es null si la sección no tiene cabecera publicada. */
public record PublicSectionResponse(String sectionKey, SectionHeading heading, List<PublicTextBlock> texts,
                                    List<PublicItem> items) {}
