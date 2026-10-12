package com.kinich.content;

import com.kinich.shared.error.ApiProblemException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Slug → sección de elementos audiovisuales; cualquier otra cosa es 404 (incluidas las de texto). */
@Component
@RequiredArgsConstructor
class ItemSectionLookup {

    private final SectionRepository sections;

    Section require(String slug) {
        SectionKey key = SectionKey.fromSlug(slug)
                .filter(ItemSectionPolicy::isItemSection)
                .orElseThrow(ApiProblemException::notFound);
        return sections.findByPageKeyAndSectionKey(PageKey.HOME, key).orElseThrow(ApiProblemException::notFound);
    }
}
