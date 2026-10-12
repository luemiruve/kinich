package com.kinich.content;

import com.kinich.shared.error.ApiProblemException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Slug → cualquier sección editable (texto o elementos). Lo usa la publicación. */
@Component
@RequiredArgsConstructor
class EditableSectionLookup {

    private final SectionRepository sections;

    Section require(String slug) {
        SectionKey key = SectionKey.fromSlug(slug)
                .filter(k -> TextSectionPolicy.isTextSection(k) || ItemSectionPolicy.isItemSection(k))
                .orElseThrow(ApiProblemException::notFound);
        return sections.findByPageKeyAndSectionKey(PageKey.HOME, key).orElseThrow(ApiProblemException::notFound);
    }
}
