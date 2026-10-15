package com.kinich.portfolio;

import com.kinich.content.Crop;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Instantánea pública de un proyecto. La etiqueta de categoría se copia (autosuficiente). Su posición es el orden
 * público; solo cambia al publicar el orden.
 */
@Getter
@Entity
@Table(name = "published_portfolio_projects")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PublishedPortfolioProject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_id", nullable = false, updatable = false)
    private UUID sourceId;

    @Column(nullable = false, length = 80)
    private String slug;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 120)
    private String eyebrow;

    @Column(length = 2000)
    private String description;

    @Column(name = "category_label", length = 60)
    private String categoryLabel;

    @Column(name = "cover_media_asset_id", nullable = false)
    private UUID coverMediaId;

    @Column(name = "cover_alt", nullable = false, length = 300)
    private String coverAlt;

    @Column(name = "cover_crop_x", nullable = false)
    private double coverCropX;

    @Column(name = "cover_crop_y", nullable = false)
    private double coverCropY;

    @Column(name = "cover_crop_width", nullable = false)
    private double coverCropWidth;

    @Column(name = "cover_crop_height", nullable = false)
    private double coverCropHeight;

    @Setter
    @Column(nullable = false)
    private int position;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "published_by")
    private UUID publishedBy;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "published_project_id", nullable = false)
    @OrderBy("position")
    private List<PublishedPortfolioProjectImage> images = new ArrayList<>();

    /** Copia los campos del borrador (sin imágenes: se añaden a {@link #getImages()}). */
    public static PublishedPortfolioProject snapshotOf(PortfolioProject draft, String categoryLabel, int position,
                                                       Instant publishedAt, UUID publishedBy) {
        PublishedPortfolioProject copy = new PublishedPortfolioProject();
        copy.sourceId = draft.getId();
        copy.slug = draft.getSlug();
        copy.title = draft.getTitle();
        copy.eyebrow = draft.getEyebrow();
        copy.description = draft.getDescription();
        copy.categoryLabel = categoryLabel;
        copy.coverMediaId = draft.getCoverMediaId();
        copy.coverAlt = draft.getCoverAlt();
        copy.coverCropX = draft.getCoverCropX();
        copy.coverCropY = draft.getCoverCropY();
        copy.coverCropWidth = draft.getCoverCropWidth();
        copy.coverCropHeight = draft.getCoverCropHeight();
        copy.position = position;
        copy.publishedAt = publishedAt;
        copy.publishedBy = publishedBy;
        return copy;
    }

    public Crop getCoverCrop() {
        return new Crop(coverCropX, coverCropY, coverCropWidth, coverCropHeight);
    }
}
