package com.kinich.content;

import com.kinich.shared.error.ApiProblemException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica UNA sección (de textos o de elementos audiovisuales): sustituye su instantánea por el borrador visible,
 * en una sola transacción. Si el borrador cambió desde la previsualización (revision distinta) no se publica nada.
 * Las secciones de Portafolio solo tienen cabecera: publicar fija la cabecera y no toca ninguna otra sección.
 */
@Slf4j(topic = "kinich.content")
@Service
@RequiredArgsConstructor
public class SectionPublicationService {

    private final EditableSectionLookup sections;
    private final TextContentRepository drafts;
    private final PublishedTextContentRepository published;
    private final ContentItemRepository itemDrafts;
    private final PublishedContentItemRepository publishedItems;
    private final ContentCategoryService categories;
    private final Clock clock;

    @Transactional
    public Section publish(PageKey page, String slug, long revision, UUID actorId) {
        Section section = sections.find(page, slug);
        if (section.getDraftRevision() != revision) {
            throw ApiProblemException.versionConflict();
        }
        // markPublished va ANTES del delete: el flush automático de este lo emite como UPDATE versionado de la
        // sección (bloquea la fila), así el segundo publicador concurrente falla por bloqueo optimista (409)
        // en lugar de chocar con la restricción única de posición (500).
        section.markPublished(actorId, clock.instant());
        SectionKey key = section.getSectionKey();
        int count = !EditableSectionLookup.hasContent(page, key) ? 0
                : TextSectionPolicy.isTextSection(key) ? publishTexts(section) : publishItems(section);
        log.info("Sección publicada (page={}, section={}, revision={}, blocks={}, userId={})", page.slug(), slug,
                revision, count, actorId);
        return section;
    }

    private int publishTexts(Section section) {
        published.deleteBySectionId(section.getId());
        List<TextContent> visible = drafts.findBySectionIdOrderByPosition(section.getId()).stream()
                .filter(block -> !block.isHidden())
                .toList();
        published.saveAll(IntStream.range(0, visible.size())
                .mapToObj(i -> PublishedTextContent.snapshotOf(visible.get(i), i + 1))
                .toList());
        return visible.size();
    }

    /** La etiqueta de la categoría se copia en la instantánea: el sitio público no depende del catálogo. */
    private int publishItems(Section section) {
        publishedItems.deleteBySectionId(section.getId());
        List<ContentItem> visible = itemDrafts.findBySectionIdOrderByPosition(section.getId()).stream()
                .filter(item -> !item.isHidden())
                .toList();
        Map<UUID, String> labels = categories.labels(visible.stream().map(ContentItem::getCategoryId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        publishedItems.saveAll(IntStream.range(0, visible.size())
                .mapToObj(i -> PublishedContentItem.snapshotOf(visible.get(i),
                        labels.get(visible.get(i).getCategoryId()), i + 1))
                .toList());
        return visible.size();
    }
}
