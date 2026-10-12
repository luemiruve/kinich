package com.kinich.brand;

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

/**
 * Recurso de identidad visual (logotipo SVG), separado de MediaAsset: sin derivados ni libvips. Guarda el SVG ya
 * sanitizado y reescrito; nunca el archivo recibido ni su nombre. Pertenece a una única variante de la configuración.
 */
@Getter
@Entity
@Table(name = "brand_assets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrandAsset extends AuditableEntity {

    public static final String SVG = "image/svg+xml";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "content_type", nullable = false, updatable = false, length = 40)
    private String contentType;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 200)
    private String storageKey;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private int sizeBytes;

    @Column(nullable = false, updatable = false, length = 64)
    private String sha256;

    @Column(name = "view_box_width", nullable = false, updatable = false)
    private double viewBoxWidth;

    @Column(name = "view_box_height", nullable = false, updatable = false)
    private double viewBoxHeight;

    static BrandAsset svg(String storageKey, SanitizedSvg svg, String sha256) {
        BrandAsset asset = new BrandAsset();
        asset.contentType = SVG;
        asset.storageKey = storageKey;
        asset.sizeBytes = svg.content().length;
        asset.sha256 = sha256;
        asset.viewBoxWidth = svg.viewBoxWidth();
        asset.viewBoxHeight = svg.viewBoxHeight();
        return asset;
    }
}
