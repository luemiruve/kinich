CREATE TABLE content_categories (
    id    UUID        PRIMARY KEY,
    label VARCHAR(60) NOT NULL,
    created_by UUID, created_at TIMESTAMPTZ NOT NULL,
    updated_by UUID, updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT content_categories_label_check CHECK (btrim(label) <> ''),
    CONSTRAINT content_categories_created_by_fk FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT content_categories_updated_by_fk FOREIGN KEY (updated_by) REFERENCES users (id)
);
CREATE UNIQUE INDEX content_categories_label_uk ON content_categories (lower(label));
