package com.kinich.content;

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
 * Escrituras sobre el BORRADOR. Ninguna toca la instantánea publicada: el público no ve cambios hasta publicar.
 * Cada escritura incrementa draft_revision de la sección; su @Version hace que dos escrituras simultáneas
 * sobre la misma sección no puedan confirmarse ambas (p. ej. dos altas en la Nota editorial).
 */
@Slf4j(topic = "kinich.content")
@Service
@RequiredArgsConstructor
public class TextContentService {

    private final TextSectionLookup sections;
    private final TextContentRepository texts;

    @Transactional
    public TextContent create(String slug, String eyebrow, String title, String body, UUID actorId) {
        Section section = sections.require(slug);
        TextValues values = TextNormalizer.normalize(eyebrow, title, body);
        long count = texts.countBySectionId(section.getId());
        Integer max = TextSectionPolicy.maxBlocks(section.getSectionKey());
        if (max != null && count >= max) {
            throw ApiProblemException.maxItemsReached();
        }
        TextContent created = texts.save(new TextContent(section.getId(), values, (int) count + 1));
        section.markDraftChanged();
        log.info("Bloque de texto creado (section={}, id={}, userId={})", slug, created.getId(), actorId);
        return created;
    }

    @Transactional
    public TextContent update(String slug, UUID id, String eyebrow, String title, String body, long version,
                              UUID actorId) {
        Section section = sections.require(slug);
        TextContent block = requireVersion(section, id, version);
        block.edit(TextNormalizer.normalize(eyebrow, title, body));
        section.markDraftChanged();
        log.info("Bloque de texto editado (section={}, id={}, userId={})", slug, id, actorId);
        return block;
    }

    @Transactional
    public TextContent changeVisibility(String slug, UUID id, boolean hidden, long version, UUID actorId) {
        Section section = sections.require(slug);
        TextContent block = requireVersion(section, id, version);
        block.changeVisibility(hidden);
        section.markDraftChanged();
        log.info("Visibilidad de bloque cambiada (section={}, id={}, hidden={}, userId={})", slug, id, hidden, actorId);
        return block;
    }

    @Transactional
    public void delete(String slug, UUID id, long version, UUID actorId) {
        Section section = sections.require(slug);
        TextContent block = requireVersion(section, id, version);
        // Primero la sección: el flush vacía su UPDATE versionado antes de bloquear la fila del bloque,
        // con el mismo orden (sección → filas) que el resto de escrituras y que publish.
        section.markDraftChanged();
        texts.delete(block);
        texts.flush();
        List<TextContent> remaining = texts.findBySectionIdOrderByPosition(section.getId());
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).moveTo(i + 1);
        }
        log.info("Bloque de texto eliminado (section={}, id={}, userId={})", slug, id, actorId);
    }

    @Transactional
    public void reorder(String slug, long revision, List<UUID> ids, UUID actorId) {
        Section section = sections.require(slug);
        if (section.getDraftRevision() != revision) {
            throw ApiProblemException.versionConflict();
        }
        Map<UUID, TextContent> current = texts.findBySectionIdOrderByPosition(section.getId()).stream()
                .collect(Collectors.toMap(TextContent::getId, Function.identity()));
        Set<UUID> requested = new HashSet<>(ids);
        if (requested.size() != ids.size() || !requested.equals(current.keySet())) {
            throw ApiProblemException.validation(
                    Map.of("ids", "debe contener exactamente los elementos de la sección, sin repetir"));
        }
        for (int i = 0; i < ids.size(); i++) {
            current.get(ids.get(i)).moveTo(i + 1);
        }
        section.markDraftChanged();
        log.info("Bloques reordenados (section={}, userId={})", slug, actorId);
    }

    private TextContent requireVersion(Section section, UUID id, long version) {
        TextContent block = texts.findByIdAndSectionId(id, section.getId())
                .orElseThrow(ApiProblemException::notFound);
        // Hibernate ignora setVersion en una entidad gestionada: la versión enviada se compara explícitamente.
        if (block.getVersion() != version) {
            throw ApiProblemException.versionConflict();
        }
        return block;
    }
}
