package com.kinich.portfolio;

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
 * Orden de los proyectos. D-15: guardar el orden solo renumera el borrador (portfolio_projects.position); el orden
 * público no cambia hasta publicarlo. No es un cambio de contenido: no incrementa draft_revision.
 */
@Slf4j(topic = "kinich.portfolio")
@Service
@RequiredArgsConstructor
public class PortfolioOrderService {

    private final PortfolioProjectRepository projects;

    /** Lista completa de proyectos en el nuevo orden, cada uno con la versión que el cliente vio. */
    @Transactional
    public void reorderDraft(List<ProjectOrderRequest.Item> requested, UUID actorId) {
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
        for (int i = 0; i < requested.size(); i++) {
            current.get(requested.get(i).id()).setPosition(i + 1);
        }
        projects.flush();
        log.info("Orden de proyectos guardado en borrador (userId={})", actorId);
    }
}
