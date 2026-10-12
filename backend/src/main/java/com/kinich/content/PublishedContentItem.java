package com.kinich.content;

import com.kinich.shared.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Copia inmutable de un elemento visible al publicar. La etiqueta de categoría se copia (instantánea autosuficiente). */
@Getter
@Entity
@Table(name = "published_content_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PublishedContentItem extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "section_id", nullable = false, updatable = false)
    private UUID sectionId;

    @Column(name = "source_id", updatable = false)
    private UUID sourceId;

    @Column(name = "media_asset_id", nullable = false, updatable = false)
    private UUID mediaId;

    @Column(name = "image_alt", nullable = false, length = 300, updatable = false)
    private String imageAlt;

    @Column(name = "category_label", length = 60, updatable = false)
    private String categoryLabel;

    @Column(length = 120, updatable = false)
    private String eyebrow;

    @Column(length = 200, updatable = false)
    private String title;

    @Column(length = 2000, updatable = false)
    private String description;

    @Column(name = "crop_x", updatable = false)
    private Double cropX;

    @Column(name = "crop_y", updatable = false)
    private Double cropY;

    @Column(name = "crop_width", updatable = false)
    private Double cropWidth;

    @Column(name = "crop_height", updatable = false)
    private Double cropHeight;

    @Column(name = "focal_x", updatable = false)
    private Double focalX;

    @Column(name = "focal_y", updatable = false)
    private Double focalY;

    @Column(nullable = false, updatable = false)
    private int position;

    /** Encuadre de esta copia publicada (rectángulo o punto focal). */
    public Framing getFraming() {
        return new Framing(cropX == null ? null : new Crop(cropX, cropY, cropWidth, cropHeight),
                focalX == null ? null : new FocalPoint(focalX, focalY));
    }

    public static PublishedContentItem snapshotOf(ContentItem draft, String categoryLabel, int position) {
        PublishedContentItem copy = new PublishedContentItem();
        copy.sectionId = draft.getSectionId();
        copy.sourceId = draft.getId();
        copy.mediaId = draft.getMediaId();
        copy.imageAlt = draft.getImageAlt();
        copy.categoryLabel = categoryLabel;
        copy.eyebrow = draft.getEyebrow();
        copy.title = draft.getTitle();
        copy.description = draft.getDescription();
        // El encuadre se congela con la instantánea: cambiarlo en el borrador no altera lo publicado.
        Framing framing = draft.getFraming();
        Crop crop = framing.crop();
        copy.cropX = crop == null ? null : crop.x();
        copy.cropY = crop == null ? null : crop.y();
        copy.cropWidth = crop == null ? null : crop.width();
        copy.cropHeight = crop == null ? null : crop.height();
        FocalPoint focal = framing.mediaPosition();
        copy.focalX = focal == null ? null : focal.x();
        copy.focalY = focal == null ? null : focal.y();
        copy.position = position;
        return copy;
    }
}
