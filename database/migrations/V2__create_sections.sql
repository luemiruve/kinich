-- Catálogo de secciones por página. Lo controla el código: el administrador no crea páginas ni secciones.
-- draft_revision / published_revision permiten saber si hay cambios sin publicar y validar la publicación.
CREATE TABLE sections (
    id                 UUID        PRIMARY KEY,
    page_key           VARCHAR(40) NOT NULL,
    section_key        VARCHAR(60) NOT NULL,
    position           INTEGER     NOT NULL,
    draft_revision     BIGINT      NOT NULL DEFAULT 0,
    published_revision BIGINT      NOT NULL DEFAULT 0,
    published_at       TIMESTAMPTZ,
    published_by       UUID,
    version            BIGINT      NOT NULL DEFAULT 0,
    created_by         UUID,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_by         UUID,
    updated_at         TIMESTAMPTZ NOT NULL,
    -- Para añadir una página: nueva migración con DROP CONSTRAINT + ADD CONSTRAINT.
    CONSTRAINT sections_page_key_check    CHECK (page_key IN ('HOME')),
    CONSTRAINT sections_section_key_check CHECK (section_key ~ '^[A-Z][A-Z_]{0,59}$'),
    CONSTRAINT sections_position_check    CHECK (position > 0),
    CONSTRAINT sections_revisions_check   CHECK (published_revision <= draft_revision),
    CONSTRAINT sections_page_section_uk   UNIQUE (page_key, section_key),
    CONSTRAINT sections_page_position_uk  UNIQUE (page_key, position),
    CONSTRAINT sections_published_by_fk   FOREIGN KEY (published_by) REFERENCES users (id),
    CONSTRAINT sections_created_by_fk     FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT sections_updated_by_fk     FOREIGN KEY (updated_by) REFERENCES users (id)
);

INSERT INTO sections (id, page_key, section_key, position, created_at, updated_at) VALUES
    ('0d1e0000-0000-4000-8000-000000000001', 'HOME', 'HERO',               1, now(), now()),
    ('0d1e0000-0000-4000-8000-000000000002', 'HOME', 'EDITORIAL_NOTE',     2, now(), now()),
    ('0d1e0000-0000-4000-8000-000000000003', 'HOME', 'DISCIPLINES',        3, now(), now()),
    ('0d1e0000-0000-4000-8000-000000000004', 'HOME', 'VALUES',             4, now(), now()),
    ('0d1e0000-0000-4000-8000-000000000005', 'HOME', 'FEATURED_SELECTION', 5, now(), now()),
    ('0d1e0000-0000-4000-8000-000000000006', 'HOME', 'CLOSING_CTA',        6, now(), now());
