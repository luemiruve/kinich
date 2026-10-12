package com.kinich.content;

import com.kinich.shared.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Elemento audiovisual en BORRADOR. Nunca lo lee la API pública. */
@Getter
@Entity
@Table(name = "content_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentItem extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "section_id", nullable = false, updatable = false)
    private UUID sectionId;

    @Column(name = "media_asset_id", nullable = false)
    private UUID mediaId;

    @Column(name = "image_alt", nullable = false, length = 300)
    private String imageAlt;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(length = 120)
    private String eyebrow;

    @Column(length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(name = "crop_x")
    private Double cropX;

    @Column(name = "crop_y")
    private Double cropY;

    @Column(name = "crop_width")
    private Double cropWidth;

    @Column(name = "crop_height")
    private Double cropHeight;

    @Column(name = "focal_x")
    private Double focalX;

    @Column(name = "focal_y")
    private Double focalY;

    @Column(nullable = false)
    private boolean hidden;

    @Column(nullable = false)
    private int position;

    @Version
    @Column(nullable = false)
    private long version;

    public ContentItem(UUID sectionId, ItemValues values, int position) {
        this.sectionId = sectionId;
        this.position = position;
        edit(values);
    }

    public void edit(ItemValues values) {
        this.mediaId = values.mediaId();
        this.imageAlt = values.imageAlt();
        this.categoryId = values.categoryId();
        this.eyebrow = values.eyebrow();
        this.title = values.title();
        this.description = values.description();
        Crop crop = values.framing().crop();
        this.cropX = crop == null ? null : crop.x();
        this.cropY = crop == null ? null : crop.y();
        this.cropWidth = crop == null ? null : crop.width();
        this.cropHeight = crop == null ? null : crop.height();
        FocalPoint focal = values.framing().mediaPosition();
        this.focalX = focal == null ? null : focal.x();
        this.focalY = focal == null ? null : focal.y();
    }

    public void changeVisibility(boolean hidden) {
        this.hidden = hidden;
    }

    public void moveTo(int position) {
        this.position = position;
    }


    /** Encuadre de este elemento (rectángulo o punto focal). */
    public Framing getFraming() {
        return new Framing(cropX == null ? null : new Crop(cropX, cropY, cropWidth, cropHeight),
                focalX == null ? null : new FocalPoint(focalX, focalY));
    }

    /** «Modificado»: cualquier diferencia con lo publicado, incluidos imagen, encuadre, texto alternativo y categoría. */
    public boolean hasSameContentAs(PublishedContentItem copy, String categoryLabel) {
        return mediaId.equals(copy.getMediaId()) && imageAlt.equals(copy.getImageAlt())
                && Objects.equals(categoryLabel, copy.getCategoryLabel()) && Objects.equals(eyebrow, copy.getEyebrow())
                && Objects.equals(title, copy.getTitle()) && Objects.equals(description, copy.getDescription())
                && getFraming().equals(copy.getFraming());
    }
}
