package com.kinich.brand;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface BrandAssetRepository extends JpaRepository<BrandAsset, UUID> {

    /** Solo un recurso referenciado ahora mismo por la configuración: uno reemplazado o suelto nunca se sirve. */
    @Query(value = "SELECT a.* FROM brand_assets a WHERE a.id = :id AND EXISTS (SELECT 1 FROM business_settings s "
            + "WHERE a.id IN (s.logo_asset_id, s.logo_on_dark_asset_id))", nativeQuery = true)
    Optional<BrandAsset> findReferenced(@Param("id") UUID id);
}
