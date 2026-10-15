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

/** Imagen de la galería de un proyecto en BORRADOR. Se muestra sin recorte. */
@Getter
@Entity
@Table(name = "portfolio_project_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioProjectImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "media_asset_id", nullable = false)
    private UUID mediaId;

    @Column(nullable = false, length = 300)
    private String alt;

    @Column(length = 300)
    private String caption;

    @Column(nullable = false)
    private int position;

    public PortfolioProjectImage(UUID mediaId, String alt, String caption, int position) {
        this.mediaId = mediaId;
        this.alt = alt;
        this.caption = caption;
        this.position = position;
    }
}
