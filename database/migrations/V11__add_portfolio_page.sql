-- Página Portafolio en el catálogo editorial. Solo cabeceras (V9): el contenido de proyectos vive en su
-- propio módulo (V12), no en content_items.
ALTER TABLE sections DROP CONSTRAINT sections_page_key_check;
ALTER TABLE sections ADD CONSTRAINT sections_page_key_check CHECK (page_key IN ('HOME', 'PORTFOLIO'));

INSERT INTO sections (id, page_key, section_key, position, created_at, updated_at) VALUES
    ('0d1e0000-0000-4000-8000-000000000101', 'PORTFOLIO', 'PAGE_HEADER', 1, now(), now()),
    ('0d1e0000-0000-4000-8000-000000000102', 'PORTFOLIO', 'CLOSING_CTA', 2, now(), now());
