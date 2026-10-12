package com.kinich.media;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Archivo físico inmutable de un medio: el original privado o una representación web. Su id es la identidad HTTP
 * de lo que se sirve (URL y ETag). La clave de almacenamiento es opaca y nunca sale del backend.
 */
@Getter
@Entity
@Table(name = "media_files")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "media_asset_id", nullable = false, updatable = false)
    private UUID mediaAssetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private MediaFileRole role;

    @Column(name = "content_type", nullable = false, updatable = false, length = 40)
    private String contentType;

    @Column(nullable = false, updatable = false)
    private int width;

    @Column(nullable = false, updatable = false)
    private int height;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 200)
    private String storageKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public MediaFile(UUID mediaAssetId, MediaFileRole role, String contentType, int width, int height, long sizeBytes,
                     String storageKey) {
        this.mediaAssetId = mediaAssetId;
        this.role = role;
        this.contentType = contentType;
        this.width = width;
        this.height = height;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
    }
}
