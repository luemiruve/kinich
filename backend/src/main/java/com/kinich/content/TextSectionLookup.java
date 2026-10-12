package com.kinich.content;

import com.kinich.shared.error.ApiProblemException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Traduce el segmento de URL a una sección textual de Inicio; cualquier otra cosa es 404. */
@Component
@RequiredArgsConstructor
class TextSectionLookup {

    private final SectionRepository sections;

    Section require(String slug) {
        SectionKey key = SectionKey.fromSlug(slug)
                .filter(TextSectionPolicy::isTextSection)
                .orElseThrow(ApiProblemException::notFound);
        return sections.findByPageKeyAndSectionKey(PageKey.HOME, key).orElseThrow(ApiProblemException::notFound);
    }
}
