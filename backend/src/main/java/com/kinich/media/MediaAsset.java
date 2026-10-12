package com.kinich.media;

import com.kinich.media.image.MediaFormat;
import com.kinich.shared.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Medio lógico que administra el estudio. Describe el original (formato, tamaño, dimensiones,
 * hash de integridad) sin rutas ni nombre original. Sus archivos físicos están en {@link MediaFile}.
 */
@Getter
@Entity
@Table(name = "media_assets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaAsset extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, updatable = false, length = 20)
    private MediaKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaAssetStatus status;

    @Column(name = "content_type", nullable = false, updatable = false, length = 40)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(nullable = false, updatable = false)
    private int width;

    @Column(nullable = false, updatable = false)
    private int height;

    @Column(nullable = false, updatable = false, length = 64)
    private String sha256;

    /** Imagen procesada durante la subida: nace lista. */
    public static MediaAsset readyImage(MediaFormat format, long sizeBytes, int width, int height, String sha256) {
        MediaAsset asset = new MediaAsset();
        asset.kind = MediaKind.IMAGE;
        asset.status = MediaAssetStatus.READY;
        asset.contentType = format.contentType();
        asset.sizeBytes = sizeBytes;
        asset.width = width;
        asset.height = height;
        asset.sha256 = sha256;
        return asset;
    }
}
