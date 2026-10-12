-- Medios: el medio lógico (media_assets) se separa de sus archivos físicos
-- (media_files). El original se conserva privado (role = ORIGINAL) y la web consume solo representaciones
-- (role = WEB) generadas al incorporar el medio. Los archivos son inmutables: reprocesar crea filas nuevas.

CREATE TABLE media_assets (
    id           UUID         PRIMARY KEY,
    media_type   VARCHAR(20)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    content_type VARCHAR(40)  NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    width        INTEGER      NOT NULL,
    height       INTEGER      NOT NULL,
    sha256       VARCHAR(64)  NOT NULL,
    created_by UUID, created_at TIMESTAMPTZ NOT NULL,
    updated_by UUID, updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT media_assets_type_check         CHECK (media_type IN ('IMAGE')),
    -- PROCESSING/FAILED quedan previstos para un futuro procesamiento en segundo plano; hoy solo se escribe READY.
    CONSTRAINT media_assets_status_check       CHECK (status IN ('PROCESSING', 'READY', 'FAILED')),
    CONSTRAINT media_assets_content_type_check CHECK (content_type IN ('image/jpeg','image/png','image/webp','image/avif')),
    CONSTRAINT media_assets_size_check         CHECK (size_bytes > 0 AND size_bytes <= 10485760),
    CONSTRAINT media_assets_width_check        CHECK (width  BETWEEN 1 AND 8000),
    CONSTRAINT media_assets_height_check       CHECK (height BETWEEN 1 AND 8000),
    CONSTRAINT media_assets_sha256_check       CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT media_assets_created_by_fk      FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT media_assets_updated_by_fk      FOREIGN KEY (updated_by) REFERENCES users (id)
);
CREATE INDEX media_assets_created_at_idx ON media_assets (created_at DESC);

CREATE TABLE media_files (
    id             UUID         PRIMARY KEY,
    media_asset_id UUID         NOT NULL,
    role           VARCHAR(20)  NOT NULL,
    content_type   VARCHAR(40)  NOT NULL,
    width          INTEGER      NOT NULL,
    height         INTEGER      NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    storage_key    VARCHAR(200) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT media_files_asset_fk       FOREIGN KEY (media_asset_id) REFERENCES media_assets (id) ON DELETE RESTRICT,
    CONSTRAINT media_files_storage_key_uk UNIQUE (storage_key),
    CONSTRAINT media_files_role_check     CHECK (role IN ('ORIGINAL', 'WEB')),
    CONSTRAINT media_files_size_check     CHECK (size_bytes > 0),
    CONSTRAINT media_files_dimensions_check CHECK (width BETWEEN 1 AND 8000 AND height BETWEEN 1 AND 8000),
    -- Representaciones web: AVIF (prioritario) y JPEG (respaldo); anchos candidatos o, si el original es más
    -- estrecho que 480 px, su propio ancho. Nunca WebP.
    CONSTRAINT media_files_kind_check CHECK (
        (role = 'ORIGINAL' AND content_type IN ('image/jpeg','image/png','image/webp','image/avif'))
        OR (role = 'WEB' AND content_type IN ('image/avif','image/jpeg')
            AND (width IN (480, 768, 1280, 1920, 2560) OR width < 480)))
);
CREATE UNIQUE INDEX media_files_one_original_uk ON media_files (media_asset_id) WHERE role = 'ORIGINAL';
CREATE UNIQUE INDEX media_files_web_rendition_uk ON media_files (media_asset_id, content_type, width) WHERE role = 'WEB';
