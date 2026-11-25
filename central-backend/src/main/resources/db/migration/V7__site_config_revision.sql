CREATE TABLE site_config_revisions (
    site_id    VARCHAR(64) PRIMARY KEY REFERENCES sites (id) ON DELETE CASCADE,
    version    BIGINT      NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

INSERT INTO site_config_revisions (site_id, version, updated_at)
SELECT id, 1, now()
FROM sites
ON CONFLICT (site_id) DO NOTHING;
