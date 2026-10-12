package com.kinich.content;

import com.kinich.media.MediaAsset;
import com.kinich.media.MediaAssetStatus;
import com.kinich.media.MediaService;
import com.kinich.shared.error.ApiProblemException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escrituras sobre el BORRADOR de elementos audiovisuales. Ninguna toca la instantánea publicada.
 * Cada escritura incrementa draft_revision de la sección; su @Version hace que dos escrituras simultáneas sobre la
 * misma sección no puedan confirmarse ambas: la perdedora recibe 409 VERSION_CONFLICT.
 */
@Slf4j(topic = "kinich.content")
@Service
@RequiredArgsConstructor
public class ContentItemService {

    /** Entrada ya validada por el DTO (tamaños); aquí se aplican limpieza, reglas de la sección y encuadre. */
    public record ItemInput(UUID mediaId, String imageAlt, UUID categoryId, String eyebrow, String title,
                            String description, Crop crop, FocalPoint mediaPosition) {}

    private final ItemSectionLookup sections;
    private final ContentItemRepository items;
    private final MediaService media;
    private final ContentCategoryService categories;

    @Transactional
    public ContentItem create(String slug, ItemInput input, UUID actorId) {
        Section section = sections.require(slug);
        ItemRules rules = ItemSectionPolicy.rules(section.getSectionKey());
        ItemValues values = validate(rules, input);
        long count = items.countBySectionId(section.getId());
        if (rules.maxItems() != null && count >= rules.maxItems()) {
            throw ApiProblemException.maxItemsReached();
        }
        ContentItem created = items.save(new ContentItem(section.getId(), values, (int) count + 1));
        section.markDraftChanged();
        log.info("Elemento creado (section={}, id={}, mediaId={}, userId={})", slug, created.getId(), values.mediaId(),
                actorId);
        return created;
    }

    @Transactional
    public ContentItem update(String slug, UUID id, ItemInput input, long version, UUID actorId) {
        Section section = sections.require(slug);
        ContentItem item = requireVersion(section, id, version);
        item.edit(validate(ItemSectionPolicy.rules(section.getSectionKey()), input));
        section.markDraftChanged();
        log.info("Elemento editado (section={}, id={}, userId={})", slug, id, actorId);
        return item;
    }

    @Transactional
    public ContentItem changeVisibility(String slug, UUID id, boolean hidden, long version, UUID actorId) {
        Section section = sections.require(slug);
        ContentItem item = requireVersion(section, id, version);
        item.changeVisibility(hidden);
        section.markDraftChanged();
        log.info("Visibilidad de elemento cambiada (section={}, id={}, hidden={}, userId={})", slug, id, hidden, actorId);
        return item;
    }

    @Transactional
    public void delete(String slug, UUID id, long version, UUID actorId) {
        Section section = sections.require(slug);
        ContentItem item = requireVersion(section, id, version);
        // Primero la sección (mismo orden sección → filas que el resto de escrituras y que publish).
        section.markDraftChanged();
        items.delete(item);
        items.flush();
        List<ContentItem> remaining = items.findBySectionIdOrderByPosition(section.getId());
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).moveTo(i + 1);
        }
        log.info("Elemento eliminado (section={}, id={}, userId={})", slug, id, actorId);
    }

    @Transactional
    public void reorder(String slug, long revision, List<UUID> ids, UUID actorId) {
        Section section = sections.require(slug);
        if (section.getDraftRevision() != revision) {
            throw ApiProblemException.versionConflict();
        }
        Map<UUID, ContentItem> current = items.findBySectionIdOrderByPosition(section.getId()).stream()
                .collect(Collectors.toMap(ContentItem::getId, Function.identity()));
        Set<UUID> requested = new HashSet<>(ids);
        if (requested.size() != ids.size() || !requested.equals(current.keySet())) {
            throw ApiProblemException.validation(
                    Map.of("ids", "debe contener exactamente los elementos de la sección, sin repetir"));
        }
        for (int i = 0; i < ids.size(); i++) {
            current.get(ids.get(i)).moveTo(i + 1);
        }
        section.markDraftChanged();
        log.info("Elementos reordenados (section={}, userId={})", slug, actorId);
    }

    private ItemValues validate(ItemRules rules, ItemInput input) {
        ItemValues values = ItemNormalizer.normalize(rules, input.mediaId(), input.imageAlt(), input.categoryId(),
                input.eyebrow(), input.title(), input.description());
        MediaAsset asset = media.findAssets(List.of(values.mediaId())).get(values.mediaId());
        if (asset == null || asset.getStatus() != MediaAssetStatus.READY) {
            throw ApiProblemException.validation(Map.of("mediaId", "no existe"));
        }
        if (values.categoryId() != null && !categories.exists(values.categoryId())) {
            throw ApiProblemException.validation(Map.of("categoryId", "no existe"));
        }
        // El encuadre se valida contra el original y se guarda en el elemento; nunca toca el medio ni sus archivos.
        return values.withFraming(rules.framing().resolve(input.crop(), input.mediaPosition(), asset.getWidth(),
                asset.getHeight()));
    }

    private ContentItem requireVersion(Section section, UUID id, long version) {
        ContentItem item = items.findByIdAndSectionId(id, section.getId()).orElseThrow(ApiProblemException::notFound);
        // Hibernate ignora setVersion en una entidad gestionada: la versión enviada se compara explícitamente.
        if (item.getVersion() != version) {
            throw ApiProblemException.versionConflict();
        }
        return item;
    }
}
