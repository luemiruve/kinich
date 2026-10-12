-- Configuración de la instalación (cada instalación configura su identidad). Se aplica al guardar:
-- no tiene borrador ni publicación. Solo identidad y contacto; la biografía y los textos editoriales son contenido.
-- Una única fila por instalación (singleton), creada aquí vacía: sin datos de ningún negocio.
CREATE TABLE business_settings (
    id                       UUID          PRIMARY KEY,
    singleton                BOOLEAN       NOT NULL DEFAULT true,
    business_name            VARCHAR(120),
    tagline                  VARCHAR(200),
    logo_media_asset_id      UUID,
    logo_dark_media_asset_id UUID,
    email                    VARCHAR(254),
    whatsapp                 VARCHAR(16),
    phone                    VARCHAR(16),
    location                 VARCHAR(120),
    address                  VARCHAR(300),
    opening_hours            VARCHAR(500),
    version                  BIGINT        NOT NULL DEFAULT 0,
    created_by UUID, created_at TIMESTAMPTZ NOT NULL,
    updated_by UUID, updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT business_settings_singleton_uk    UNIQUE (singleton),
    CONSTRAINT business_settings_singleton_check CHECK (singleton),
    -- Un logotipo en uso no se puede borrar mientras la instalación lo referencie.
    CONSTRAINT business_settings_logo_fk      FOREIGN KEY (logo_media_asset_id) REFERENCES media_assets (id) ON DELETE RESTRICT,
    CONSTRAINT business_settings_logo_dark_fk FOREIGN KEY (logo_dark_media_asset_id) REFERENCES media_assets (id) ON DELETE RESTRICT,
    CONSTRAINT business_settings_name_check     CHECK (business_name IS NULL OR btrim(business_name) <> ''),
    CONSTRAINT business_settings_tagline_check  CHECK (tagline IS NULL OR btrim(tagline) <> ''),
    CONSTRAINT business_settings_email_check    CHECK (email IS NULL OR email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'),
    -- Teléfonos en formato E.164 normalizado (+ y hasta 15 dígitos).
    CONSTRAINT business_settings_whatsapp_check CHECK (whatsapp IS NULL OR whatsapp ~ '^\+[1-9][0-9]{6,14}$'),
    CONSTRAINT business_settings_phone_check    CHECK (phone IS NULL OR phone ~ '^\+[1-9][0-9]{6,14}$'),
    CONSTRAINT business_settings_location_check CHECK (location IS NULL OR btrim(location) <> ''),
    CONSTRAINT business_settings_address_check  CHECK (address IS NULL OR btrim(address) <> ''),
    CONSTRAINT business_settings_hours_check    CHECK (opening_hours IS NULL OR btrim(opening_hours) <> ''),
    CONSTRAINT business_settings_created_by_fk  FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT business_settings_updated_by_fk  FOREIGN KEY (updated_by) REFERENCES users (id)
);

INSERT INTO business_settings (id, created_at, updated_at) VALUES (gen_random_uuid(), now(), now());

-- Redes sociales: colección ordenable. OTHER permite una plataforma no listada (su nombre visible es obligatorio).
-- Solo enlaces externos HTTPS.
CREATE TABLE social_links (
    id         UUID          PRIMARY KEY,
    platform   VARCHAR(20)   NOT NULL,
    label      VARCHAR(60),
    url        VARCHAR(2048) NOT NULL,
    visible    BOOLEAN       NOT NULL DEFAULT true,
    position   INTEGER       NOT NULL,
    version    BIGINT        NOT NULL DEFAULT 0,
    created_by UUID, created_at TIMESTAMPTZ NOT NULL,
    updated_by UUID, updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT social_links_platform_check CHECK (platform IN
        ('INSTAGRAM', 'FACEBOOK', 'TIKTOK', 'YOUTUBE', 'VIMEO', 'PINTEREST', 'X', 'LINKEDIN', 'OTHER')),
    CONSTRAINT social_links_label_check    CHECK (label IS NULL OR btrim(label) <> ''),
    CONSTRAINT social_links_other_check    CHECK (platform <> 'OTHER' OR label IS NOT NULL),
    CONSTRAINT social_links_url_check      CHECK (url LIKE 'https://%'),
    CONSTRAINT social_links_position_check CHECK (position > 0),
    CONSTRAINT social_links_created_by_fk  FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT social_links_updated_by_fk  FOREIGN KEY (updated_by) REFERENCES users (id)
);
CREATE INDEX social_links_position_idx ON social_links (position);
