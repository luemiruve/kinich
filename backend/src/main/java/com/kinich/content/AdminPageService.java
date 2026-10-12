package com.kinich.content;

import com.kinich.media.MediaAsset;
import com.kinich.media.MediaAudience;
import com.kinich.media.MediaRenditions;
import com.kinich.media.MediaService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Vista de administración: borrador + estados derivados + revisiones (lo que se previsualiza y publica). */
@Service
@RequiredArgsConstructor
public class AdminPageService {

    /** Orden fijo y nombres iguales a los del JSON de entrada. */
    private static final List<ItemField> FIELD_ORDER =
            List.of(ItemField.EYEBROW, ItemField.CATEGORY, ItemField.TITLE, ItemField.DESCRIPTION);

    private final SectionRepository sections;
    private final TextContentRepository drafts;
    private final PublishedTextContentRepository published;
    private final ContentItemRepository itemDrafts;
    private final PublishedContentItemRepository publishedItems;
    private final ContentCategoryService categories;
    private final MediaService media;

    @Transactional(readOnly = true)
    public AdminPageResponse load() {
        List<Section> all = sections.findByPageKeyOrderByPosition(PageKey.HOME);
        List<UUID> textSectionIds = all.stream()
                .filter(section -> TextSectionPolicy.isTextSection(section.getSectionKey()))
                .map(Section::getId)
                .toList();
        List<UUID> itemSectionIds = all.stream()
                .filter(section -> ItemSectionPolicy.isItemSection(section.getSectionKey()))
                .map(Section::getId)
                .toList();
        Map<UUID, List<TextContent>> draftsBySection = drafts.findBySectionIdInOrderByPosition(textSectionIds)
                .stream().collect(Collectors.groupingBy(TextContent::getSectionId));
        Map<UUID, PublishedTextContent> publishedBySource = published.findBySectionIdInOrderByPosition(textSectionIds)
                .stream().filter(copy -> copy.getSourceId() != null)
                .collect(Collectors.toMap(PublishedTextContent::getSourceId, Function.identity()));
        List<ContentItem> items = itemDrafts.findBySectionIdInOrderByPosition(itemSectionIds);
        Map<UUID, List<ContentItem>> itemsBySection = items.stream()
                .collect(Collectors.groupingBy(ContentItem::getSectionId));
        Map<UUID, PublishedContentItem> publishedItemsBySource = publishedItems
                .findBySectionIdInOrderByPosition(itemSectionIds).stream()
                .filter(copy -> copy.getSourceId() != null)
                .collect(Collectors.toMap(PublishedContentItem::getSourceId, Function.identity()));
        ItemLookups lookups = lookups(items);
        return new AdminPageResponse(PageKey.HOME.slug(), all.stream()
                .map(section -> toResponse(section, draftsBySection.getOrDefault(section.getId(), List.of()),
                        publishedBySource, itemsBySection.getOrDefault(section.getId(), List.of()),
                        publishedItemsBySource, lookups))
                .toList());
    }

    @Transactional(readOnly = true)
    public TextBlockResponse describe(TextContent block) {
        return toResponse(block, published.findBySourceId(block.getId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public ItemResponse describe(ContentItem item) {
        return toResponse(item, publishedItems.findBySourceId(item.getId()).orElse(null), lookups(List.of(item)));
    }

    /** Etiquetas de categoría y medios en lote (sin N+1). */
    private ItemLookups lookups(List<ContentItem> items) {
        Set<UUID> categoryIds = items.stream().map(ContentItem::getCategoryId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> mediaIds = items.stream().map(ContentItem::getMediaId).collect(Collectors.toSet());
        return new ItemLookups(categories.labels(categoryIds), media.findAssets(mediaIds),
                media.renditions(mediaIds, MediaAudience.ADMIN));
    }

    private record ItemLookups(Map<UUID, String> labels, Map<UUID, MediaAsset> assets,
                               Map<UUID, MediaRenditions> renditions) {}

    private static AdminSectionResponse toResponse(Section section, List<TextContent> blocks,
                                                   Map<UUID, PublishedTextContent> publishedBySource,
                                                   List<ContentItem> items,
                                                   Map<UUID, PublishedContentItem> publishedItemsBySource,
                                                   ItemLookups lookups) {
        SectionKey key = section.getSectionKey();
        boolean itemEditable = ItemSectionPolicy.isItemSection(key);
        ItemRules rules = itemEditable ? ItemSectionPolicy.rules(key) : null;
        return new AdminSectionResponse(key.slug(), TextSectionPolicy.isTextSection(key),
                TextSectionPolicy.maxBlocks(key), itemEditable, rules == null ? null : rules.maxItems(),
                rules == null ? List.of() : fieldNames(rules.allowed()),
                rules == null ? List.of() : fieldNames(rules.required()),
                rules == null ? null : rules.framing(),
                SectionHeadingPolicy.fieldNames(key), section.draftHeading(),
                section.getDraftRevision(), section.getPublishedRevision(),
                section.hasUnpublishedChanges(), section.getPublishedAt(),
                blocks.stream().map(block -> toResponse(block, publishedBySource.get(block.getId()))).toList(),
                items.stream().map(item -> toResponse(item, publishedItemsBySource.get(item.getId()), lookups))
                        .toList());
    }

    private static List<String> fieldNames(Set<ItemField> fields) {
        return FIELD_ORDER.stream().filter(fields::contains).map(AdminPageService::fieldName).toList();
    }

    private static String fieldName(ItemField field) {
        return switch (field) {
            case EYEBROW -> "eyebrow";
            case CATEGORY -> "categoryId";
            case TITLE -> "title";
            case DESCRIPTION -> "description";
        };
    }

    private static TextBlockResponse toResponse(TextContent block, PublishedTextContent copy) {
        ContentStatus status = block.isHidden() ? ContentStatus.HIDDEN
                : copy != null ? ContentStatus.PUBLISHED : ContentStatus.DRAFT;
        boolean modified = copy != null && !block.hasSameContentAs(copy);
        return new TextBlockResponse(block.getId(), block.getEyebrow(), block.getTitle(), block.getBody(),
                block.isHidden(), block.getPosition(), block.getVersion(), status, modified);
    }

    private static ItemResponse toResponse(ContentItem item, PublishedContentItem copy, ItemLookups lookups) {
        String label = item.getCategoryId() == null ? null : lookups.labels().get(item.getCategoryId());
        ContentStatus status = item.isHidden() ? ContentStatus.HIDDEN
                : copy != null ? ContentStatus.PUBLISHED : ContentStatus.DRAFT;
        boolean modified = copy != null && !item.hasSameContentAs(copy, label);
        MediaAsset asset = lookups.assets().get(item.getMediaId());
        MediaRenditions renditions = lookups.renditions().get(item.getMediaId());
        Framing framing = item.getFraming();
        return new ItemResponse(item.getId(),
                new ImageResponse(item.getImageAlt(), asset.getWidth(), asset.getHeight(), renditions.sources(),
                        renditions.fallback(), framing.crop(), framing.mediaPosition()),
                item.getMediaId(), item.getCategoryId() == null ? null : new CategoryRef(item.getCategoryId(), label),
                item.getEyebrow(), item.getTitle(), item.getDescription(), item.isHidden(), item.getPosition(),
                item.getVersion(), status, modified);
    }
}
