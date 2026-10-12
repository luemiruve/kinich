package com.kinich.settings;

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

/** Colección de redes sociales de la instalación. Se aplica al guardar; cada enlace lleva su propia versión. */
@Slf4j(topic = "kinich.settings")
@Service
@RequiredArgsConstructor
public class SocialLinkService {

    /** Límite técnico de la colección (protege el pie y la petición), no una regla de negocio. */
    public static final int MAX_LINKS = 20;

    private final SocialLinkRepository links;

    @Transactional(readOnly = true)
    public List<SocialLink> list() {
        return links.findAllByOrderByPosition();
    }

    @Transactional(readOnly = true)
    public List<SocialLink> listVisible() {
        return links.findByVisibleTrueOrderByPosition();
    }

    @Transactional
    public SocialLink create(SocialPlatform platform, String label, String url, boolean visible, UUID actorId) {
        SocialLinkValues values = SettingsNormalizer.socialLink(platform, label, url, visible);
        long count = links.count();
        if (count >= MAX_LINKS) {
            throw ApiProblemException.maxItemsReached();
        }
        SocialLink created = links.saveAndFlush(new SocialLink(values, (int) count + 1));
        log.info("Red social creada (id={}, platform={}, userId={})", created.getId(), platform, actorId);
        return created;
    }

    @Transactional
    public SocialLink update(UUID id, SocialPlatform platform, String label, String url, boolean visible,
                             long version, UUID actorId) {
        SocialLink link = requireVersion(id, version);
        link.edit(SettingsNormalizer.socialLink(platform, label, url, visible));
        links.flush();
        log.info("Red social editada (id={}, userId={})", id, actorId);
        return link;
    }

    @Transactional
    public void delete(UUID id, long version, UUID actorId) {
        links.delete(requireVersion(id, version));
        links.flush();
        List<SocialLink> remaining = links.findAllByOrderByPosition();
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).moveTo(i + 1);
        }
        log.info("Red social eliminada (id={}, userId={})", id, actorId);
    }

    /** Nuevo orden completo; cada enlace debe llegar con la versión que el cliente vio (concurrencia optimista). */
    @Transactional
    public void reorder(Map<UUID, Long> versionsInOrder, List<UUID> ids, UUID actorId) {
        Map<UUID, SocialLink> current = links.findAllByOrderByPosition().stream()
                .collect(Collectors.toMap(SocialLink::getId, Function.identity()));
        Set<UUID> requested = new HashSet<>(ids);
        if (requested.size() != ids.size() || !requested.equals(current.keySet())) {
            throw ApiProblemException.validation(
                    Map.of("items", "debe contener exactamente los enlaces existentes, sin repetir"));
        }
        for (UUID id : ids) {
            if (current.get(id).getVersion() != versionsInOrder.get(id)) {
                throw ApiProblemException.versionConflict();
            }
        }
        for (int i = 0; i < ids.size(); i++) {
            current.get(ids.get(i)).moveTo(i + 1);
        }
        links.flush();
        log.info("Redes sociales reordenadas (userId={})", actorId);
    }

    private SocialLink requireVersion(UUID id, long version) {
        SocialLink link = links.findById(id).orElseThrow(ApiProblemException::notFound);
        if (link.getVersion() != version) {
            throw ApiProblemException.versionConflict();
        }
        return link;
    }
}
