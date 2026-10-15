package com.kinich.media;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaFileRepository extends JpaRepository<MediaFile, UUID> {

    List<MediaFile> findByMediaAssetIdInAndRole(Collection<UUID> mediaAssetIds, MediaFileRole role);

    /** Solo representaciones web: el original nunca es resoluble por id desde un endpoint. */
    Optional<MediaFile> findByIdAndRole(UUID id, MediaFileRole role);

    /**
     * Lo único que puede servir la API pública: una representación web de un medio que alguna instantánea publicada
     * referencia: un item de Inicio, la portada de un proyecto del Portafolio o una imagen de su galería. Los borradores,
     * los proyectos retirados una vez publicados y el original nunca se sirven; un medio compartido sigue sirviéndose
     * mientras quede al menos una referencia publicada. Los logotipos ya no son medios (V10): se sirven como recursos de
     * marca SVG.
     */
    @Query(value = "SELECT f.* FROM media_files f WHERE f.id = :id AND f.role = 'WEB' AND ("
            + "EXISTS (SELECT 1 FROM published_content_items p WHERE p.media_asset_id = f.media_asset_id) "
            + "OR EXISTS (SELECT 1 FROM published_portfolio_projects pp WHERE pp.cover_media_asset_id = f.media_asset_id) "
            + "OR EXISTS (SELECT 1 FROM published_portfolio_project_images pi WHERE pi.media_asset_id = f.media_asset_id))",
            nativeQuery = true)
    Optional<MediaFile> findPublishedWebFile(@Param("id") UUID id);
}
