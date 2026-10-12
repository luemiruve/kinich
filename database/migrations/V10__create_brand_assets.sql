-- Identidad visual SVG. El logotipo no es un medio (MediaAsset):
-- es un recurso de marca propio, un SVG sanitizado y reescrito por el backend. MediaAsset sigue sin admitir SVG.
-- Los límites de tamaño, elementos y profundidad son técnicos y viven en la configuración (kinich.brand.svg).
CREATE TABLE brand_assets (
    id              UUID             PRIMARY KEY,
    content_type    VARCHAR(40)      NOT NULL,
    storage_key     VARCHAR(200)     NOT NULL,
    size_bytes      INTEGER          NOT NULL,
    sha256          VARCHAR(64)      NOT NULL,
    view_box_width  DOUBLE PRECISION NOT NULL,
    view_box_height DOUBLE PRECISION NOT NULL,
    created_by UUID, created_at TIMESTAMPTZ NOT NULL,
    updated_by UUID, updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT brand_assets_content_type_check CHECK (content_type = 'image/svg+xml'),
    CONSTRAINT brand_assets_storage_key_uk     UNIQUE (storage_key),
    CONSTRAINT brand_assets_size_check         CHECK (size_bytes > 0),
    CONSTRAINT brand_assets_sha256_check       CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT brand_assets_view_box_check     CHECK (view_box_width > 0 AND view_box_height > 0),
    CONSTRAINT brand_assets_created_by_fk      FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT brand_assets_updated_by_fk      FOREIGN KEY (updated_by) REFERENCES users (id)
);

-- Los logotipos raster de V8 no se conservan como respaldo: el logotipo maestro es SVG. Los medios que estuvieran
-- referenciados siguen existiendo en la biblioteca de medios; solo se retira la referencia.
ALTER TABLE business_settings
    DROP CONSTRAINT business_settings_logo_fk,
    DROP CONSTRAINT business_settings_logo_dark_fk,
    DROP COLUMN logo_media_asset_id,
    DROP COLUMN logo_dark_media_asset_id,
    -- Principal: se usa en todos los temas (un SVG con prefers-color-scheme cubre ambos con esta sola variante).
    ADD COLUMN logo_asset_id UUID,
    -- Opcional: sustituye al principal solo con fondo oscuro.
    ADD COLUMN logo_on_dark_asset_id UUID,
    ADD CONSTRAINT business_settings_logo_asset_fk
        FOREIGN KEY (logo_asset_id) REFERENCES brand_assets (id) ON DELETE RESTRICT,
    ADD CONSTRAINT business_settings_logo_on_dark_asset_fk
        FOREIGN KEY (logo_on_dark_asset_id) REFERENCES brand_assets (id) ON DELETE RESTRICT,
    ADD CONSTRAINT business_settings_logo_on_dark_requires_logo_check
        CHECK (logo_on_dark_asset_id IS NULL OR logo_asset_id IS NOT NULL),
    -- Cada recurso de marca pertenece a una única variante.
    ADD CONSTRAINT business_settings_logo_variants_distinct_check
        CHECK (logo_on_dark_asset_id IS NULL OR logo_on_dark_asset_id <> logo_asset_id);
