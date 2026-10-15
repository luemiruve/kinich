package com.kinich.portfolio;

import com.kinich.content.ContentCategoryService;
import com.kinich.content.Crop;
import com.kinich.content.ItemFraming;
import com.kinich.media.MediaAsset;
import com.kinich.media.MediaAssetStatus;
import com.kinich.media.MediaService;
import com.kinich.shared.error.ApiProblemException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escrituras sobre el BORRADOR de proyectos. Ninguna toca la instantánea publicada, salvo eliminar (D-16), que la
 * borra en cascada y renumera el orden público. Cada cambio de contenido incrementa draft_revision; la versión enviada
 * se compara explícitamente y @Version queda como respaldo ante escrituras simultáneas.
 */
@Slf4j(topic = "kinich.portfolio")
@Service
@RequiredArgsConstructor
public class PortfolioProjectService {

    private static final String SLUG_TAKEN = "ya está en uso";
    private static final String SLUG_CONSTRAINT = "portfolio_projects_slug_uk";

    private final PortfolioProjectRepository projects;
    private final PublishedPortfolioProjectRepository published;
    private final MediaService media;
    private final ContentCategoryService categories;

    @Transactional
    public PortfolioProject create(ProjectRequest request, UUID actorId) {
        // Mismo orden de bloqueo que publicar: serializa el recuento (límite) y la posición max+1 entre creaciones.
        published.lockForPublication();
        ProjectValues values = ProjectNormalizer.normalize(request);
        Crop crop = validate(values, null);
        if (projects.count() >= PortfolioLimits.MAX_PROJECTS) {
            throw ApiProblemException.maxItemsReached();
        }
        int position = projects.findTopByOrderByPositionDesc().map(PortfolioProject::getPosition).orElse(0) + 1;
        PortfolioProject project = new PortfolioProject(values.slug(), values.title(), values.coverMediaId(),
                values.coverAlt(), crop, position);
        apply(project, values, crop);
        PortfolioProject created = saveCheckingSlug(project);
        log.info("Proyecto creado (id={}, slug={}, userId={})", created.getId(), created.getSlug(), actorId);
        return created;
    }

    /** Reemplaza los datos y la galería completa del borrador. */
    @Transactional
    public PortfolioProject update(UUID id, ProjectRequest request, UUID actorId) {
        PortfolioProject project = projects.findById(id).orElseThrow(ApiProblemException::notFound);
        if (request.version() == null) {
            throw ApiProblemException.validation(Map.of("version", "es obligatorio"));
        }
        requireVersion(project, request.version());
        ProjectValues values = ProjectNormalizer.normalize(request);
        Crop crop = validate(values, id);
        apply(project, values, crop);
        project.setDraftRevision(project.getDraftRevision() + 1);
        saveCheckingSlug(project);
        log.info("Proyecto editado (id={}, userId={})", id, actorId);
        return project;
    }

    /** D-16: ocultar solo cambia el borrador; el proyecto sigue público hasta que se publique. */
    @Transactional
    public PortfolioProject changeVisibility(UUID id, boolean hidden, long version, UUID actorId) {
        PortfolioProject project = require(id, version);
        if (project.isHidden() == hidden) {
            // Sin cambio real: ni nueva revisión ni nueva versión.
            return project;
        }
        project.setHidden(hidden);
        project.setDraftRevision(project.getDraftRevision() + 1);
        projects.flush();
        log.info("Visibilidad de proyecto cambiada (id={}, hidden={}, userId={})", id, hidden, actorId);
        return project;
    }

    /**
     * D-16: inmediato. La base borra en cascada imágenes e instantánea; después se renumeran 1..n el borrador y el
     * orden público (la UNIQUE de posiciones publicadas es diferida, así que el orden de las actualizaciones no importa).
     */
    @Transactional
    public void delete(UUID id, long version, UUID actorId) {
        // Bloqueo antes de leer el borrador, como al publicar: sin interbloqueo con una publicación simultánea y con
        // renumeraciones serializadas entre eliminaciones.
        published.lockForPublication();
        projects.delete(require(id, version));
        projects.flush();
        List<PortfolioProject> remaining = projects.findAllByOrderByPositionAsc();
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setPosition(i + 1);
        }
        List<PublishedPortfolioProject> publicOrder = published.findAllByOrderByPositionAsc();
        for (int i = 0; i < publicOrder.size(); i++) {
            publicOrder.get(i).setPosition(i + 1);
        }
        projects.flush();
        log.info("Proyecto eliminado (id={}, userId={})", id, actorId);
    }

    /** Medios, categoría, slug y recorte de portada contra la base; devuelve el recorte resuelto (centrado si falta). */
    private Crop validate(ProjectValues values, UUID id) {
        List<UUID> ids = new ArrayList<>();
        ids.add(values.coverMediaId());
        values.images().forEach(image -> ids.add(image.mediaId()));
        Map<UUID, MediaAsset> assets = media.findAssets(ids);
        Map<String, String> errors = new LinkedHashMap<>();
        MediaAsset cover = assets.get(values.coverMediaId());
        if (!ready(cover)) {
            errors.put("cover.mediaId", "no existe");
        }
        for (int i = 0; i < values.images().size(); i++) {
            if (!ready(assets.get(values.images().get(i).mediaId()))) {
                errors.put("images[" + i + "].mediaId", "no existe");
            }
        }
        if (values.categoryId() != null && !categories.exists(values.categoryId())) {
            errors.put("categoryId", "no existe");
        }
        if (slugTaken(values.slug(), id)) {
            errors.put("slug", SLUG_TAKEN);
        }
        if (!errors.isEmpty()) {
            throw ApiProblemException.validation(errors);
        }
        // El recorte se valida contra el original (4:5, dentro de la imagen): nunca llega a la CHECK de la base.
        return ItemFraming.CROP_4_5.resolve(values.coverCrop(), null, cover.getWidth(), cover.getHeight()).crop();
    }

    /** Otro borrador o la instantánea de OTRO proyecto (el propio puede conservar su slug publicado). */
    private boolean slugTaken(String slug, UUID id) {
        if (id == null) {
            return projects.existsBySlug(slug) || published.existsBySlug(slug);
        }
        return projects.existsBySlugAndIdNot(slug, id) || published.existsBySlugAndSourceIdNot(slug, id);
    }

    private static boolean ready(MediaAsset asset) {
        return asset != null && asset.getStatus() == MediaAssetStatus.READY;
    }

    /** Galería completa: las filas anteriores se eliminan (orphanRemoval) y la UNIQUE diferida tolera el cruce. */
    private static void apply(PortfolioProject project, ProjectValues values, Crop crop) {
        project.setSlug(values.slug());
        project.setTitle(values.title());
        project.setEyebrow(values.eyebrow());
        project.setDescription(values.description());
        project.setCategoryId(values.categoryId());
        project.setCoverMediaId(values.coverMediaId());
        project.setCoverAlt(values.coverAlt());
        project.setCoverCrop(crop);
        project.getImages().clear();
        for (int i = 0; i < values.images().size(); i++) {
            ProjectImageValues image = values.images().get(i);
            project.getImages().add(new PortfolioProjectImage(image.mediaId(), image.alt(), image.caption(), i + 1));
        }
    }

    /** Una carrera entre dos guardados con el mismo slug acaba en el mismo 400 que la comprobación previa. */
    private PortfolioProject saveCheckingSlug(PortfolioProject project) {
        try {
            return projects.saveAndFlush(project);
        } catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof ConstraintViolationException violation
                    && SLUG_CONSTRAINT.equals(violation.getConstraintName())) {
                throw ApiProblemException.validation(Map.of("slug", SLUG_TAKEN));
            }
            throw e;
        }
    }

    private PortfolioProject require(UUID id, long version) {
        PortfolioProject project = projects.findById(id).orElseThrow(ApiProblemException::notFound);
        requireVersion(project, version);
        return project;
    }

    private static void requireVersion(PortfolioProject project, long version) {
        // Hibernate ignora setVersion en una entidad gestionada: la versión enviada se compara explícitamente.
        if (project.getVersion() != version) {
            throw ApiProblemException.versionConflict();
        }
    }
}
