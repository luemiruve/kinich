package com.kinich.portfolio;

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

/** Copia inmutable de una imagen de galería al publicar el proyecto. */
@Getter
@Entity
@Table(name = "published_portfolio_project_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PublishedPortfolioProjectImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "media_asset_id", nullable = false, updatable = false)
    private UUID mediaId;

    @Column(nullable = false, length = 300, updatable = false)
    private String alt;

    @Column(length = 300, updatable = false)
    private String caption;

    @Column(nullable = false, updatable = false)
    private int position;

    public PublishedPortfolioProjectImage(UUID mediaId, String alt, String caption, int position) {
        this.mediaId = mediaId;
        this.alt = alt;
        this.caption = caption;
        this.position = position;
    }
}
