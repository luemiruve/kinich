-- Portafolio: módulo propio (no content_items). portfolio_projects / portfolio_project_images = borrador
-- (solo administración); published_* = instantánea pública. Publicar es por proyecto; el orden público
-- (published_portfolio_projects.position) solo cambia al publicar el orden. Encuadre 4:5 solo de la portada;
-- la galería se muestra sin recorte. Nunca se modifica el original.

CREATE TABLE portfolio_projects (
    id                   UUID          PRIMARY KEY,
    slug                 VARCHAR(80)   NOT NULL,
    title                VARCHAR(200)  NOT NULL,
    eyebrow              VARCHAR(120),
    description          VARCHAR(2000),
    category_id          UUID,
    cover_media_asset_id UUID          NOT NULL,
    cover_alt            VARCHAR(300)  NOT NULL,
    cover_crop_x         DOUBLE PRECISION NOT NULL,
    cover_crop_y         DOUBLE PRECISION NOT NULL,
    cover_crop_width     DOUBLE PRECISION NOT NULL,
    cover_crop_height    DOUBLE PRECISION NOT NULL,
    hidden               BOOLEAN       NOT NULL DEFAULT false,
    position             INTEGER       NOT NULL,
    draft_revision       BIGINT        NOT NULL DEFAULT 1,
    published_revision   BIGINT        NOT NULL DEFAULT 0,
    version              BIGINT        NOT NULL DEFAULT 0,
    created_by UUID, created_at TIMESTAMPTZ NOT NULL,
    updated_by UUID, updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT portfolio_projects_slug_uk        UNIQUE (slug),
    CONSTRAINT portfolio_projects_slug_check     CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT portfolio_projects_title_check    CHECK (btrim(title) <> ''),
    CONSTRAINT portfolio_projects_eyebrow_check  CHECK (eyebrow IS NULL OR btrim(eyebrow) <> ''),
    CONSTRAINT portfolio_projects_description_check CHECK (description IS NULL OR btrim(description) <> ''),
    CONSTRAINT portfolio_projects_cover_alt_check CHECK (btrim(cover_alt) <> ''),
    CONSTRAINT portfolio_projects_cover_crop_check CHECK (cover_crop_x >= 0 AND cover_crop_y >= 0
        AND cover_crop_width > 0 AND cover_crop_height > 0
        AND cover_crop_x + cover_crop_width <= 1.000001 AND cover_crop_y + cover_crop_height <= 1.000001),
    CONSTRAINT portfolio_projects_position_check  CHECK (position > 0),
    CONSTRAINT portfolio_projects_revisions_check CHECK (published_revision <= draft_revision),
    CONSTRAINT portfolio_projects_category_fk FOREIGN KEY (category_id) REFERENCES content_categories (id) ON DELETE RESTRICT,
    CONSTRAINT portfolio_projects_cover_fk    FOREIGN KEY (cover_media_asset_id) REFERENCES media_assets (id) ON DELETE RESTRICT,
    CONSTRAINT portfolio_projects_created_by_fk FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT portfolio_projects_updated_by_fk FOREIGN KEY (updated_by) REFERENCES users (id)
);
CREATE INDEX portfolio_projects_position_idx ON portfolio_projects (position);
CREATE INDEX portfolio_projects_cover_idx    ON portfolio_projects (cover_media_asset_id);

CREATE TABLE portfolio_project_images (
    id             UUID         PRIMARY KEY,
    project_id     UUID         NOT NULL,
    media_asset_id UUID         NOT NULL,
    alt            VARCHAR(300) NOT NULL,
    caption        VARCHAR(300),
    position       INTEGER      NOT NULL,
    CONSTRAINT portfolio_project_images_project_fk FOREIGN KEY (project_id) REFERENCES portfolio_projects (id) ON DELETE CASCADE,
    CONSTRAINT portfolio_project_images_media_fk   FOREIGN KEY (media_asset_id) REFERENCES media_assets (id) ON DELETE RESTRICT,
    CONSTRAINT portfolio_project_images_alt_check     CHECK (btrim(alt) <> ''),
    CONSTRAINT portfolio_project_images_caption_check CHECK (caption IS NULL OR btrim(caption) <> ''),
    CONSTRAINT portfolio_project_images_position_check CHECK (position > 0),
    CONSTRAINT portfolio_project_images_position_uk UNIQUE (project_id, position) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX portfolio_project_images_media_idx ON portfolio_project_images (media_asset_id);

CREATE TABLE published_portfolio_projects (
    id                   UUID          PRIMARY KEY,
    source_id            UUID          NOT NULL,
    slug                 VARCHAR(80)   NOT NULL,
    title                VARCHAR(200)  NOT NULL,
    eyebrow              VARCHAR(120),
    description          VARCHAR(2000),
    category_label       VARCHAR(60),
    cover_media_asset_id UUID          NOT NULL,
    cover_alt            VARCHAR(300)  NOT NULL,
    cover_crop_x         DOUBLE PRECISION NOT NULL,
    cover_crop_y         DOUBLE PRECISION NOT NULL,
    cover_crop_width     DOUBLE PRECISION NOT NULL,
    cover_crop_height    DOUBLE PRECISION NOT NULL,
    position             INTEGER       NOT NULL,
    published_at         TIMESTAMPTZ   NOT NULL,
    published_by         UUID,
    CONSTRAINT published_portfolio_projects_source_uk UNIQUE (source_id),
    CONSTRAINT published_portfolio_projects_slug_uk   UNIQUE (slug),
    CONSTRAINT published_portfolio_projects_position_uk UNIQUE (position) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT published_portfolio_projects_slug_check  CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT published_portfolio_projects_title_check CHECK (btrim(title) <> ''),
    CONSTRAINT published_portfolio_projects_category_check CHECK (category_label IS NULL OR btrim(category_label) <> ''),
    CONSTRAINT published_portfolio_projects_cover_alt_check CHECK (btrim(cover_alt) <> ''),
    CONSTRAINT published_portfolio_projects_position_check CHECK (position > 0),
    CONSTRAINT published_portfolio_projects_source_fk FOREIGN KEY (source_id) REFERENCES portfolio_projects (id) ON DELETE CASCADE,
    CONSTRAINT published_portfolio_projects_cover_fk  FOREIGN KEY (cover_media_asset_id) REFERENCES media_assets (id) ON DELETE RESTRICT,
    CONSTRAINT published_portfolio_projects_published_by_fk FOREIGN KEY (published_by) REFERENCES users (id)
);
CREATE INDEX published_portfolio_projects_cover_idx ON published_portfolio_projects (cover_media_asset_id);

CREATE TABLE published_portfolio_project_images (
    id                   UUID         PRIMARY KEY,
    published_project_id UUID         NOT NULL,
    media_asset_id       UUID         NOT NULL,
    alt                  VARCHAR(300) NOT NULL,
    caption              VARCHAR(300),
    position             INTEGER      NOT NULL,
    CONSTRAINT published_portfolio_project_images_project_fk FOREIGN KEY (published_project_id)
        REFERENCES published_portfolio_projects (id) ON DELETE CASCADE,
    CONSTRAINT published_portfolio_project_images_media_fk FOREIGN KEY (media_asset_id) REFERENCES media_assets (id) ON DELETE RESTRICT,
    CONSTRAINT published_portfolio_project_images_alt_check CHECK (btrim(alt) <> ''),
    CONSTRAINT published_portfolio_project_images_position_check CHECK (position > 0),
    CONSTRAINT published_portfolio_project_images_position_uk UNIQUE (published_project_id, position)
);
CREATE INDEX published_portfolio_project_images_media_idx ON published_portfolio_project_images (media_asset_id);
