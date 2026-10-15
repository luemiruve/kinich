package com.kinich.portfolio;

import com.kinich.shared.error.ApiProblemException;
import java.util.Comparator;
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
 * Orden de los proyectos. D-15: guardar el orden solo renumera el borrador (portfolio_projects.position); el orden
 * público no cambia hasta publicarlo. No es un cambio de contenido: no incrementa draft_revision. Publicar el orden
 * (R-1) copia a las instantáneas el orden relativo del borrador.
 */
@Slf4j(topic = "kinich.portfolio")
@Service
@RequiredArgsConstructor
public class PortfolioOrderService {

    private final PortfolioProjectRepository projects;
    private final PublishedPortfolioProjectRepository published;

    /** Lista completa de proyectos en el nuevo orden, cada uno con la versión que el cliente vio. */
    @Transactional
    public void reorderDraft(List<ProjectOrderRequest.Item> requested, UUID actorId) {
        // Mismo orden de bloqueo que crear, eliminar y publicar: las posiciones del borrador no se pisan.
        published.lockForPublication();
        Map<UUID, PortfolioProject> current = requireCurrent(requested);
        for (int i = 0; i < requested.size(); i++) {
            current.get(requested.get(i).id()).setPosition(i + 1);
        }
        projects.flush();
        log.info("Orden de proyectos guardado en borrador (userId={})", actorId);
    }

    /**
     * R-1: cada instantánea toma la posición relativa (1..n) de su proyecto en el borrador; los proyectos sin
     * instantánea se ignoran. La lista se valida como al guardar el orden (las versiones garantizan que el cliente vio
     * el orden actual del borrador). No cambia ningún borrador ni su versión.
     */
    @Transactional
    public void publishOrder(List<ProjectOrderRequest.Item> requested, UUID actorId) {
        published.lockForPublication();
        Map<UUID, PortfolioProject> current = requireCurrent(requested);
        Map<UUID, PublishedPortfolioProject> bySource = published.findAll().stream()
                .collect(Collectors.toMap(PublishedPortfolioProject::getSourceId, Function.identity()));
        int position = 0;
        // El mismo conjunto ya validado, ordenado por la posición del borrador (sin una segunda consulta).
        List<PortfolioProject> draftOrder = current.values().stream()
                .sorted(Comparator.comparingInt(PortfolioProject::getPosition)).toList();
        for (PortfolioProject project : draftOrder) {
            PublishedPortfolioProject copy = bySource.get(project.getId());
            if (copy != null) {
                // La UNIQUE de posiciones publicadas es diferida: el cruce intermedio no importa.
                copy.setPosition(++position);
            }
        }
        published.flush();
        log.info("Orden de proyectos publicado ({} proyectos, userId={})", position, actorId);
    }

    /** Exactamente los proyectos existentes, sin repetir (400), y cada uno con su versión actual (409). */
    private Map<UUID, PortfolioProject> requireCurrent(List<ProjectOrderRequest.Item> requested) {
        Map<UUID, PortfolioProject> current = projects.findAllByOrderByPositionAsc().stream()
                .collect(Collectors.toMap(PortfolioProject::getId, Function.identity()));
        Set<UUID> ids = new HashSet<>();
        requested.forEach(item -> ids.add(item.id()));
        if (ids.size() != requested.size() || !ids.equals(current.keySet())) {
            throw ApiProblemException.validation(
                    Map.of("projects", "debe contener exactamente los proyectos existentes, sin repetir"));
        }
        for (ProjectOrderRequest.Item item : requested) {
            if (current.get(item.id()).getVersion() != item.version()) {
                throw ApiProblemException.versionConflict();
            }
        }
        return current;
    }
}
