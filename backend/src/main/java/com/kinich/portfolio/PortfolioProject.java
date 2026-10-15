package com.kinich.portfolio;

import com.kinich.content.Crop;
import com.kinich.shared.audit.AuditableEntity;
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
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Proyecto de portafolio en BORRADOR. Nunca lo lee la API pública. */
@Getter
@Entity
@Table(name = "portfolio_projects")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioProject extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Setter
    @Column(nullable = false, length = 80)
    private String slug;

    @Setter
    @Column(nullable = false, length = 200)
    private String title;

    @Setter
    @Column(length = 120)
    private String eyebrow;

    @Setter
    @Column(length = 2000)
    private String description;

    @Setter
    @Column(name = "category_id")
    private UUID categoryId;

    @Setter
    @Column(name = "cover_media_asset_id", nullable = false)
    private UUID coverMediaId;

    @Setter
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
    private boolean hidden;

    @Setter
    @Column(nullable = false)
    private int position;

    @Setter
    @Column(name = "draft_revision", nullable = false)
    private long draftRevision = 1;

    @Setter
    @Column(name = "published_revision", nullable = false)
    private long publishedRevision;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "project_id", nullable = false)
    @OrderBy("position")
    private List<PortfolioProjectImage> images = new ArrayList<>();

    public PortfolioProject(String slug, String title, UUID coverMediaId, String coverAlt, Crop coverCrop, int position) {
        this.slug = slug;
        this.title = title;
        this.coverMediaId = coverMediaId;
        this.coverAlt = coverAlt;
        this.position = position;
        setCoverCrop(coverCrop);
    }

    public Crop getCoverCrop() {
        return new Crop(coverCropX, coverCropY, coverCropWidth, coverCropHeight);
    }

    public void setCoverCrop(Crop crop) {
        this.coverCropX = crop.x();
        this.coverCropY = crop.y();
        this.coverCropWidth = crop.width();
        this.coverCropHeight = crop.height();
    }
}
