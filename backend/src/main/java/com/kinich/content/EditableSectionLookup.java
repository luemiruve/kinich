package com.kinich.content;

import com.kinich.shared.error.ApiProblemException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Página + slug → sección editable de esa página. Lo usan la cabecera y la publicación. En Inicio, toda sección
 * de texto o de elementos; en Portafolio, solo las que tienen cabecera. Una sección de otra página es 404.
 */
@Component
@RequiredArgsConstructor
class EditableSectionLookup {

    private final SectionRepository sections;

    Section find(PageKey page, String slug) {
        SectionKey key = SectionKey.fromSlug(slug)
                .filter(k -> isEditable(page, k))
                .orElseThrow(ApiProblemException::notFound);
        return sections.findByPageKeyAndSectionKey(page, key).orElseThrow(ApiProblemException::notFound);
    }

    /** Las políticas de textos y elementos son de Inicio: Portafolio no tiene content_items ni text_contents. */
    static boolean hasContent(PageKey page, SectionKey key) {
        return page == PageKey.HOME && (TextSectionPolicy.isTextSection(key) || ItemSectionPolicy.isItemSection(key));
    }

    private static boolean isEditable(PageKey page, SectionKey key) {
        return hasContent(page, key) || !SectionHeadingPolicy.fields(page, key).isEmpty();
    }
}
