-- Usuarios del panel de administración. En esta etapa solo existe el fotógrafo.
-- El correo es el nombre de usuario y se guarda normalizado en minúsculas.
CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by    UUID         REFERENCES users (id),
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_by    UUID         REFERENCES users (id),
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email))
);
