ALTER TABLE actors DROP CONSTRAINT ck_actors_role;

ALTER TABLE actors
    ADD CONSTRAINT ck_actors_role CHECK (role IN ('VIEWER', 'OPERATOR', 'SUPERVISOR', 'ONBOARDING_ADMIN')),
    ADD COLUMN password_hash VARCHAR(100),
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE auth_sessions (
    token_hash   VARCHAR(64) PRIMARY KEY,
    actor_id     VARCHAR(255) NOT NULL REFERENCES actors (actor_id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ  NOT NULL,
    revoked_at   TIMESTAMPTZ
);

CREATE INDEX idx_auth_sessions_actor_id ON auth_sessions (actor_id);
CREATE INDEX idx_auth_sessions_expires_at ON auth_sessions (expires_at);

CREATE TABLE edge_site_credentials (
    id           UUID PRIMARY KEY,
    site_id      VARCHAR(64)  NOT NULL REFERENCES sites (id) ON DELETE CASCADE,
    label        VARCHAR(128) NOT NULL,
    token_prefix VARCHAR(16)  NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL UNIQUE,
    created_by   VARCHAR(255) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    revoked_at   TIMESTAMPTZ
);

CREATE INDEX idx_edge_site_credentials_site_active
    ON edge_site_credentials (site_id)
    WHERE revoked_at IS NULL;