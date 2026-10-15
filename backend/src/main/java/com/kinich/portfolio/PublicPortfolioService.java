package com.kinich.portfolio;

import com.kinich.content.Crop;
import com.kinich.content.ImageResponse;
import com.kinich.content.PageKey;
import com.kinich.content.PublicPageService;
import com.kinich.content.SectionKey;
import com.kinich.media.MediaAsset;
import com.kinich.media.MediaAudience;
import com.kinich.media.MediaRenditions;
import com.kinich.media.MediaService;
import com.kinich.shared.error.ApiProblemException;
import java.text.Collator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Portafolio público: lee SOLO las instantáneas publicadas (nunca las tablas de borrador). */
@Service
@RequiredArgsConstructor
public class PublicPortfolioService {

    private final PublishedPortfolioProjectRepository projects;
    private final PublicPageService pages;
    private final MediaService media;

    @Transactional(readOnly = true)
    public PublicPortfolioPageResponse page() {
        List<PublishedPortfolioProject> published = projects.findAllByOrderByPositionAsc();
        Map<UUID, MediaAsset> assets = media.findAssets(coverIds(published));
        Map<UUID, MediaRenditions> renditions = media.renditions(coverIds(published), MediaAudience.PUBLIC);
        List<PublicProjectCard> cards = published.stream()
                .map(p -> new PublicProjectCard(p.getSlug(), p.getTitle(), p.getEyebrow(), p.getCategoryLabel(),
                        p.getDescription(), image(assets.get(p.getCoverMediaId()), renditions.get(p.getCoverMediaId()),
                                p.getCoverAlt(), p.getCoverCrop())))
                .toList();
        // R-3: etiquetas distintas de los proyectos publicados, alfabéticas en español (Ñ tras N).
        Collator spanish = Collator.getInstance(Locale.forLanguageTag("es"));
        List<String> categories = published.stream().map(PublishedPortfolioProject::getCategoryLabel)
                .filter(Objects::nonNull).distinct().sorted(spanish).toList();
        var headings = pages.headings(PageKey.PORTFOLIO);
        return new PublicPortfolioPageResponse(headings.get(SectionKey.PAGE_HEADER),
                headings.get(SectionKey.CLOSING_CTA), categories, cards);
    }

    /** Cualquier slug sin instantánea (borrador, retirado, antiguo o mal formado) da el mismo 404. */
    @Transactional(readOnly = true)
    public PublicProjectResponse detail(String slug) {
        PublishedPortfolioProject project = projects.findBySlug(slug).orElseThrow(ApiProblemException::notFound);
        Set<UUID> ids = new HashSet<>();
        project.getImages().forEach(i -> ids.add(i.getMediaId()));
        Map<UUID, MediaAsset> assets = media.findAssets(ids);
        Map<UUID, MediaRenditions> renditions = media.renditions(ids, MediaAudience.PUBLIC);
        List<PublicProjectImage> images = project.getImages().stream()
                .map(i -> new PublicProjectImage(image(assets.get(i.getMediaId()), renditions.get(i.getMediaId()),
                        i.getAlt(), null), i.getCaption()))
                .toList();
        return new PublicProjectResponse(project.getSlug(), project.getTitle(), project.getEyebrow(),
                project.getCategoryLabel(), project.getDescription(), images);
    }

    private static Set<UUID> coverIds(List<PublishedPortfolioProject> published) {
        Set<UUID> ids = new HashSet<>();
        published.forEach(p -> ids.add(p.getCoverMediaId()));
        return ids;
    }

    /** El FK RESTRICT garantiza que el medio existe. Solo URLs de representaciones web; nunca el original. */
    private static ImageResponse image(MediaAsset asset, MediaRenditions renditions, String alt, Crop crop) {
        return new ImageResponse(alt, asset.getWidth(), asset.getHeight(), renditions.sources(),
                renditions.fallback(), crop, null);
    }
}
