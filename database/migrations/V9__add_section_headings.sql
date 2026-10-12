-- Textos de cabecera de cada sección. Son contenido editorial: el borrador se edita
-- y la copia publicada se fija al publicar la sección, como el resto de su contenido. Qué campos admite cada
-- sección lo decide el código (SectionHeadingPolicy): el administrador edita textos, no la estructura.
ALTER TABLE sections
    ADD COLUMN heading_eyebrow           VARCHAR(120),
    ADD COLUMN heading_title             VARCHAR(200),
    ADD COLUMN heading_body              VARCHAR(600),
    ADD COLUMN published_heading_eyebrow VARCHAR(120),
    ADD COLUMN published_heading_title   VARCHAR(200),
    ADD COLUMN published_heading_body    VARCHAR(600),
    ADD CONSTRAINT sections_heading_check CHECK (
        (heading_eyebrow IS NULL OR btrim(heading_eyebrow) <> '')
        AND (heading_title IS NULL OR btrim(heading_title) <> '')
        AND (heading_body IS NULL OR btrim(heading_body) <> ''));
