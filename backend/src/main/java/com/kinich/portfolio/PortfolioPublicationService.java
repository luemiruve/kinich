package com.kinich.portfolio;

import com.kinich.content.ContentCategoryService;
import com.kinich.shared.error.ApiProblemException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica UN proyecto: sustituye su instantánea por el borrador, o la elimina si el borrador está oculto (D-16). No
 * publica el orden de los demás (R-1). Nunca hace público nada que no esté en el borrador que se publica.
 */
@Slf4j(topic = "kinich.portfolio")
@Service
@RequiredArgsConstructor
public class PortfolioPublicationService {

    private static final String SLUG_TAKEN = "ya está en uso";
    private static final String SLUG_CONSTRAINT = "published_portfolio_projects_slug_uk";

    private final PortfolioProjectRepository projects;
    private final PublishedPortfolioProjectRepository published;
    private final ContentCategoryService categories;
    private final EntityManager entityManager;
    private final Clock clock;

    @Transactional
    public PortfolioProject publish(UUID id, long version, UUID actorId) {
        // Primero el bloqueo: la segunda publicación concurrente lee el borrador ya publicado y recibe 409.
        published.lockForPublication();
        PortfolioProject project = projects.findById(id).orElseThrow(ApiProblemException::notFound);
        // Hibernate ignora setVersion en una entidad gestionada: la versión enviada se compara explícitamente.
        if (project.getVersion() != version) {
            throw ApiProblemException.versionConflict();
        }
        PublishedPortfolioProject current = published.findBySourceId(id).orElse(null);
        if (project.isHidden()) {
            if (current == null) {
                // R-5: oculto y nunca publicado (o ya retirado): nada que cambiar.
                return project;
            }
            withdraw(current);
            log.info("Proyecto retirado del sitio público (id={}, userId={})", id, actorId);
        } else {
            replaceSnapshot(project, current, actorId);
            log.info("Proyecto publicado (id={}, slug={}, revision={}, userId={})", id, project.getSlug(),
                    project.getDraftRevision(), actorId);
        }
        markPublished(project);
        return project;
    }

    /** D-16 y Review Focus 3: sin la instantánea, el orden público se renumera 1..n conservando el orden relativo. */
    private void withdraw(PublishedPortfolioProject current) {
        published.delete(current);
        published.flush();
        List<PublishedPortfolioProject> publicOrder = published.findAllByOrderByPositionAsc();
        for (int i = 0; i < publicOrder.size(); i++) {
            publicOrder.get(i).setPosition(i + 1);
        }
        published.flush();
    }

    /**
     * Conserva la posición pública si ya estaba publicado; si es nuevo, al final (R-2). La instantánea anterior se
     * borra y se confirma ANTES de insertar la nueva: Hibernate emite inserciones antes que borrados y la UNIQUE de
     * posiciones de la galería publicada no es diferida.
     */
    private void replaceSnapshot(PortfolioProject project, PublishedPortfolioProject current, UUID actorId) {
        if (published.existsBySlugAndSourceIdNot(project.getSlug(), project.getId())) {
            throw ApiProblemException.validation(Map.of("slug", SLUG_TAKEN));
        }
        int position;
        if (current != null) {
            position = current.getPosition();
            published.delete(current);
            published.flush();
        } else {
            position = (int) published.count() + 1;
        }
        UUID categoryId = project.getCategoryId();
        String label = categoryId == null ? null : categories.labels(Set.of(categoryId)).get(categoryId);
        PublishedPortfolioProject copy = PublishedPortfolioProject.snapshotOf(project, label, position,
                clock.instant(), actorId);
        List<PortfolioProjectImage> gallery = project.getImages();
        for (int i = 0; i < gallery.size(); i++) {
            PortfolioProjectImage image = gallery.get(i);
            copy.getImages().add(new PublishedPortfolioProjectImage(image.getMediaId(), image.getAlt(),
                    image.getCaption(), i + 1));
        }
        try {
            published.saveAndFlush(copy);
        } catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof ConstraintViolationException violation
                    && SLUG_CONSTRAINT.equals(violation.getConstraintName())) {
                throw ApiProblemException.validation(Map.of("slug", SLUG_TAKEN));
            }
            throw e;
        }
    }

    /** published_revision = draft_revision; la versión sube siempre, también al republicar sin cambios. */
    private void markPublished(PortfolioProject project) {
        if (project.getPublishedRevision() != project.getDraftRevision()) {
            project.setPublishedRevision(project.getDraftRevision());
        } else {
            entityManager.lock(project, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        }
        projects.flush();
    }
}
