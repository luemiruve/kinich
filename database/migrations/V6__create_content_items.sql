-- content_items = borrador (solo administración). published_content_items = instantánea publicada
-- (solo API pública). Publicar sustituye la instantánea de una sección en una transacción.
-- Encuadre por elemento: rectángulo normalizado 0–1 sobre el original (crop_*) o punto focal
-- (focal_*, mediaPosition); exactamente uno de los dos. Se copia a la instantánea al publicar. Nunca modifica
-- el original ni las representaciones.

CREATE TABLE content_items (
    id          UUID          PRIMARY KEY,
    section_id  UUID          NOT NULL,
    media_asset_id UUID       NOT NULL,
    image_alt   VARCHAR(300)  NOT NULL,
    category_id UUID,
    eyebrow     VARCHAR(120),
    title       VARCHAR(200),
    description VARCHAR(2000),
    crop_x      DOUBLE PRECISION,
    crop_y      DOUBLE PRECISION,
    crop_width  DOUBLE PRECISION,
    crop_height DOUBLE PRECISION,
    focal_x     DOUBLE PRECISION,
    focal_y     DOUBLE PRECISION,
    hidden      BOOLEAN       NOT NULL DEFAULT false,
    position    INTEGER       NOT NULL,
    version     BIGINT        NOT NULL DEFAULT 0,
    created_by UUID, created_at TIMESTAMPTZ NOT NULL,
    updated_by UUID, updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT content_items_section_fk   FOREIGN KEY (section_id)  REFERENCES sections (id),
    CONSTRAINT content_items_media_fk     FOREIGN KEY (media_asset_id) REFERENCES media_assets (id) ON DELETE RESTRICT,
    CONSTRAINT content_items_category_fk  FOREIGN KEY (category_id) REFERENCES content_categories (id) ON DELETE RESTRICT,
    CONSTRAINT content_items_alt_check         CHECK (btrim(image_alt) <> ''),
    CONSTRAINT content_items_eyebrow_check     CHECK (eyebrow IS NULL OR btrim(eyebrow) <> ''),
    CONSTRAINT content_items_title_check       CHECK (title IS NULL OR btrim(title) <> ''),
    CONSTRAINT content_items_description_check CHECK (description IS NULL OR btrim(description) <> ''),
    CONSTRAINT content_items_position_check    CHECK (position > 0),
    CONSTRAINT content_items_framing_check   CHECK (num_nonnulls(crop_x, focal_x) = 1),
    CONSTRAINT content_items_crop_group_check CHECK (num_nonnulls(crop_x, crop_y, crop_width, crop_height) IN (0, 4)),
    CONSTRAINT content_items_crop_range_check CHECK (crop_x IS NULL OR (crop_x >= 0 AND crop_y >= 0 AND crop_width > 0
        AND crop_height > 0 AND crop_x + crop_width <= 1.000001 AND crop_y + crop_height <= 1.000001)),
    CONSTRAINT content_items_focal_group_check CHECK (num_nonnulls(focal_x, focal_y) IN (0, 2)),
    CONSTRAINT content_items_focal_range_check CHECK (focal_x IS NULL OR (focal_x BETWEEN 0 AND 1 AND focal_y BETWEEN 0 AND 1)),
    CONSTRAINT content_items_created_by_fk FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT content_items_updated_by_fk FOREIGN KEY (updated_by) REFERENCES users (id)
);
CREATE INDEX content_items_section_position_idx ON content_items (section_id, position);
CREATE INDEX content_items_media_idx ON content_items (media_asset_id);

CREATE TABLE published_content_items (
    id             UUID          PRIMARY KEY,
    section_id     UUID          NOT NULL,
    source_id      UUID,
    media_asset_id UUID          NOT NULL,
    image_alt      VARCHAR(300)  NOT NULL,
    category_label VARCHAR(60),
    eyebrow        VARCHAR(120),
    title          VARCHAR(200),
    description    VARCHAR(2000),
    crop_x      DOUBLE PRECISION,
    crop_y      DOUBLE PRECISION,
    crop_width  DOUBLE PRECISION,
    crop_height DOUBLE PRECISION,
    focal_x     DOUBLE PRECISION,
    focal_y     DOUBLE PRECISION,
    position       INTEGER       NOT NULL,
    created_by UUID, created_at TIMESTAMPTZ NOT NULL,
    updated_by UUID, updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT published_content_items_section_fk FOREIGN KEY (section_id) REFERENCES sections (id),
    CONSTRAINT published_content_items_source_fk  FOREIGN KEY (source_id)  REFERENCES content_items (id) ON DELETE SET NULL,
    CONSTRAINT published_content_items_media_fk   FOREIGN KEY (media_asset_id) REFERENCES media_assets (id) ON DELETE RESTRICT,
    CONSTRAINT published_content_items_alt_check      CHECK (btrim(image_alt) <> ''),
    CONSTRAINT published_content_items_category_check CHECK (category_label IS NULL OR btrim(category_label) <> ''),
    CONSTRAINT published_content_items_eyebrow_check  CHECK (eyebrow IS NULL OR btrim(eyebrow) <> ''),
    CONSTRAINT published_content_items_title_check    CHECK (title IS NULL OR btrim(title) <> ''),
    CONSTRAINT published_content_items_description_check CHECK (description IS NULL OR btrim(description) <> ''),
    CONSTRAINT published_content_items_position_check CHECK (position > 0),
    CONSTRAINT published_content_items_framing_check   CHECK (num_nonnulls(crop_x, focal_x) = 1),
    CONSTRAINT published_content_items_crop_group_check CHECK (num_nonnulls(crop_x, crop_y, crop_width, crop_height) IN (0, 4)),
    CONSTRAINT published_content_items_crop_range_check CHECK (crop_x IS NULL OR (crop_x >= 0 AND crop_y >= 0 AND crop_width > 0
        AND crop_height > 0 AND crop_x + crop_width <= 1.000001 AND crop_y + crop_height <= 1.000001)),
    CONSTRAINT published_content_items_focal_group_check CHECK (num_nonnulls(focal_x, focal_y) IN (0, 2)),
    CONSTRAINT published_content_items_focal_range_check CHECK (focal_x IS NULL OR (focal_x BETWEEN 0 AND 1 AND focal_y BETWEEN 0 AND 1)),
    CONSTRAINT published_content_items_section_position_uk UNIQUE (section_id, position),
    CONSTRAINT published_content_items_created_by_fk FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT published_content_items_updated_by_fk FOREIGN KEY (updated_by) REFERENCES users (id)
);
CREATE INDEX published_content_items_media_idx ON published_content_items (media_asset_id);
