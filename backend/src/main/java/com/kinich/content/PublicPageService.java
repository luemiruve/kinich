package com.kinich.content;

import com.kinich.media.MediaAsset;
import com.kinich.media.MediaAudience;
import com.kinich.media.MediaRenditions;
import com.kinich.media.MediaService;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Página pública: SOLO la instantánea publicada. Consultas en lote, sin N+1. */
@Service
@RequiredArgsConstructor
public class PublicPageService {

    private final SectionRepository sections;
    private final PublishedTextContentRepository published;
    private final PublishedContentItemRepository publishedItems;
    private final MediaService media;

    @Transactional(readOnly = true)
    public PublicPageResponse load() {
        return load(PageKey.HOME);
    }

    /** Solo las cabeceras PUBLICADAS de una página (sin borradores); las secciones sin cabecera no aparecen. */
    @Transactional(readOnly = true)
    public Map<SectionKey, SectionHeading> headings(PageKey page) {
        Map<SectionKey, SectionHeading> result = new EnumMap<>(SectionKey.class);
        for (Section section : sections.findByPageKeyOrderByPosition(page)) {
            SectionHeading heading = section.publishedHeading();
            if (heading != null) {
                result.put(section.getSectionKey(), heading);
            }
        }
        return result;
    }

    private PublicPageResponse load(PageKey page) {
        List<Section> all = sections.findByPageKeyOrderByPosition(page);
        List<UUID> sectionIds = all.stream().map(Section::getId).toList();
        Map<UUID, List<PublicTextBlock>> textsBySection = published
                .findBySectionIdInOrderByPosition(sectionIds).stream()
                .collect(Collectors.groupingBy(PublishedTextContent::getSectionId,
                        Collectors.mapping(PublicTextBlock::from, Collectors.toList())));
        List<PublishedContentItem> copies = publishedItems.findBySectionIdInOrderByPosition(sectionIds);
        // El FK ON DELETE RESTRICT garantiza que todo medio referenciado existe. Solo URLs públicas de
        // representaciones web; el original nunca forma parte de la respuesta.
        Set<UUID> assetIds = copies.stream().map(PublishedContentItem::getMediaId).collect(Collectors.toSet());
        Map<UUID, MediaAsset> assets = media.findAssets(assetIds);
        Map<UUID, MediaRenditions> renditions = media.renditions(assetIds, MediaAudience.PUBLIC);
        Map<UUID, List<PublicItem>> itemsBySection = copies.stream()
                .collect(Collectors.groupingBy(PublishedContentItem::getSectionId,
                        Collectors.mapping(copy -> toPublicItem(copy, assets.get(copy.getMediaId()),
                                renditions.get(copy.getMediaId())), Collectors.toList())));
        return new PublicPageResponse(page.slug(), all.stream()
                .map(section -> new PublicSectionResponse(section.getSectionKey().slug(), section.publishedHeading(),
                        textsBySection.getOrDefault(section.getId(), List.of()),
                        itemsBySection.getOrDefault(section.getId(), List.of())))
                .toList());
    }

    private static PublicItem toPublicItem(PublishedContentItem copy, MediaAsset asset, MediaRenditions renditions) {
        Framing framing = copy.getFraming();
        return new PublicItem(copy.getId(),
                new ImageResponse(copy.getImageAlt(), asset.getWidth(), asset.getHeight(), renditions.sources(),
                        renditions.fallback(), framing.crop(), framing.mediaPosition()),
                copy.getCategoryLabel(), copy.getEyebrow(), copy.getTitle(), copy.getDescription());
    }
}
