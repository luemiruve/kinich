-- text_contents = borrador (solo administración). published_text_contents = instantánea publicada
-- (solo API pública). Publicar sustituye la instantánea de una sección en una transacción.
CREATE TABLE text_contents (
    id         UUID          PRIMARY KEY,
    section_id UUID          NOT NULL,
    eyebrow    VARCHAR(120),
    title      VARCHAR(200),
    body       VARCHAR(2000) NOT NULL,
    hidden     BOOLEAN       NOT NULL DEFAULT false,
    position   INTEGER       NOT NULL,
    version    BIGINT        NOT NULL DEFAULT 0,
    created_by UUID, created_at TIMESTAMPTZ NOT NULL,
    updated_by UUID, updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT text_contents_section_fk    FOREIGN KEY (section_id) REFERENCES sections (id),
    CONSTRAINT text_contents_body_check    CHECK (btrim(body) <> ''),
    CONSTRAINT text_contents_eyebrow_check CHECK (eyebrow IS NULL OR btrim(eyebrow) <> ''),
    CONSTRAINT text_contents_title_check   CHECK (title IS NULL OR btrim(title) <> ''),
    CONSTRAINT text_contents_position_check CHECK (position > 0),
    CONSTRAINT text_contents_created_by_fk FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT text_contents_updated_by_fk FOREIGN KEY (updated_by) REFERENCES users (id)
);
CREATE INDEX text_contents_section_position_idx ON text_contents (section_id, position);

CREATE TABLE published_text_contents (
    id         UUID          PRIMARY KEY,
    section_id UUID          NOT NULL,
    source_id  UUID,
    eyebrow    VARCHAR(120),
    title      VARCHAR(200),
    body       VARCHAR(2000) NOT NULL,
    position   INTEGER       NOT NULL,
    created_by UUID, created_at TIMESTAMPTZ NOT NULL,
    updated_by UUID, updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT published_text_contents_section_fk FOREIGN KEY (section_id) REFERENCES sections (id),
    CONSTRAINT published_text_contents_source_fk  FOREIGN KEY (source_id) REFERENCES text_contents (id) ON DELETE SET NULL,
    CONSTRAINT published_text_contents_body_check CHECK (btrim(body) <> ''),
    CONSTRAINT published_text_contents_position_check CHECK (position > 0),
    CONSTRAINT published_text_contents_section_position_uk UNIQUE (section_id, position),
    CONSTRAINT published_text_contents_created_by_fk FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT published_text_contents_updated_by_fk FOREIGN KEY (updated_by) REFERENCES users (id)
);
