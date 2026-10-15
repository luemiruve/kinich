package com.kinich.portfolio;

import com.kinich.content.CategoryRef;
import com.kinich.content.ContentCategoryService;
import com.kinich.content.Crop;
import com.kinich.content.ImageResponse;
import com.kinich.media.MediaAsset;
import com.kinich.media.MediaAudience;
import com.kinich.media.MediaRenditions;
import com.kinich.media.MediaService;
import com.kinich.shared.error.ApiProblemException;
import java.util.HashSet;
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

/**
 * Vista de administración del Portafolio: borrador + estado derivado de la instantánea. Imágenes con representaciones
 * de administración en lote (sin N+1); nunca el original ni claves de almacenamiento.
 */
@Service
@RequiredArgsConstructor
public class AdminPortfolioReader {

    private final PortfolioProjectRepository projects;
    private final PublishedPortfolioProjectRepository published;
    private final ContentCategoryService categories;
    private final MediaService media;

    @Transactional(readOnly = true)
    public AdminProjectListResponse list() {
        List<PortfolioProject> drafts = projects.findAllByOrderByPositionAsc();
        List<PublishedPortfolioProject> publicOrder = published.findAllByOrderByPositionAsc();
        Map<UUID, PublishedPortfolioProject> bySource = publicOrder.stream()
                .collect(Collectors.toMap(PublishedPortfolioProject::getSourceId, Function.identity()));
        Lookups lookups = lookups(drafts, drafts.stream().map(PortfolioProject::getCoverMediaId).collect(Collectors.toSet()));
        List<AdminProjectSummary> summaries = drafts.stream().map(project -> {
            PublishedPortfolioProject copy = bySource.get(project.getId());
            return new AdminProjectSummary(project.getId(), project.getSlug(), project.getTitle(),
                    lookups.category(project), lookups.cover(project), status(project, copy), modified(project, copy),
                    project.isHidden(), project.getPosition(), project.getVersion());
        }).toList();
        // Orden relativo de los publicados en el borrador frente al orden público.
        List<UUID> draftOrder = drafts.stream().map(PortfolioProject::getId).filter(bySource::containsKey).toList();
        List<UUID> publishedOrder = publicOrder.stream().map(PublishedPortfolioProject::getSourceId).toList();
        return new AdminProjectListResponse(summaries, !draftOrder.equals(publishedOrder));
    }

    @Transactional(readOnly = true)
    public AdminProjectResponse project(UUID id) {
        PortfolioProject project = projects.findById(id).orElseThrow(ApiProblemException::notFound);
        PublishedPortfolioProject copy = published.findBySourceId(id).orElse(null);
        Set<UUID> mediaIds = new HashSet<>();
        mediaIds.add(project.getCoverMediaId());
        project.getImages().forEach(image -> mediaIds.add(image.getMediaId()));
        Lookups lookups = lookups(List.of(project), mediaIds);
        List<AdminProjectImage> images = project.getImages().stream()
                .map(image -> new AdminProjectImage(image.getMediaId(),
                        lookups.image(image.getMediaId(), image.getAlt(), null), image.getCaption()))
                .toList();
        return new AdminProjectResponse(project.getId(), project.getSlug(), copy == null ? null : copy.getSlug(),
                project.getTitle(), project.getEyebrow(), lookups.category(project), project.getDescription(),
                lookups.cover(project), project.getCoverMediaId(), images, status(project, copy),
                modified(project, copy), project.isHidden(), project.getPosition(), project.getVersion());
    }

    /** Con instantánea sigue público aunque el borrador esté oculto (D-16). */
    private static ProjectStatus status(PortfolioProject project, PublishedPortfolioProject copy) {
        if (copy != null) {
            return ProjectStatus.PUBLISHED;
        }
        return project.isHidden() ? ProjectStatus.HIDDEN : ProjectStatus.DRAFT;
    }

    private static boolean modified(PortfolioProject project, PublishedPortfolioProject copy) {
        return copy != null && project.getPublishedRevision() < project.getDraftRevision();
    }

    private Lookups lookups(List<PortfolioProject> drafts, Set<UUID> mediaIds) {
        Set<UUID> categoryIds = drafts.stream().map(PortfolioProject::getCategoryId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return new Lookups(categories.labels(categoryIds), media.findAssets(mediaIds),
                media.renditions(mediaIds, MediaAudience.ADMIN));
    }

    private record Lookups(Map<UUID, String> labels, Map<UUID, MediaAsset> assets,
                           Map<UUID, MediaRenditions> renditions) {

        CategoryRef category(PortfolioProject project) {
            UUID id = project.getCategoryId();
            return id == null ? null : new CategoryRef(id, labels.get(id));
        }

        ImageResponse cover(PortfolioProject project) {
            return image(project.getCoverMediaId(), project.getCoverAlt(), project.getCoverCrop());
        }

        /** Galería sin recorte (crop nulo); portada con su recorte 4:5. */
        ImageResponse image(UUID mediaId, String alt, Crop crop) {
            MediaAsset asset = assets.get(mediaId);
            MediaRenditions files = renditions.get(mediaId);
            return new ImageResponse(alt, asset.getWidth(), asset.getHeight(),
                    files == null ? List.of() : files.sources(),
                    files == null ? null : files.fallback(), crop, null);
        }
    }
}
