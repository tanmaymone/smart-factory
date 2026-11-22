CREATE TABLE sites (
    id         VARCHAR(64)  PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    timezone   VARCHAR(64)  NOT NULL,
    status     VARCHAR(32)  NOT NULL
);

CREATE TABLE lines (
    site_id VARCHAR(64)  NOT NULL REFERENCES sites (id),
    id      VARCHAR(64)  NOT NULL,
    name    VARCHAR(255) NOT NULL,
    PRIMARY KEY (site_id, id)
);

CREATE TABLE tag_mappings (
    id                 UUID         PRIMARY KEY,
    site_id            VARCHAR(64)  NOT NULL,
    line_id            VARCHAR(64)  NOT NULL,
    tag_code           VARCHAR(128) NOT NULL,
    description        VARCHAR(255) NOT NULL,
    protocol           VARCHAR(32)  NOT NULL,
    endpoint_url       VARCHAR(512) NOT NULL,
    external_address   VARCHAR(255) NOT NULL,
    data_type          VARCHAR(32)  NOT NULL,
    unit               VARCHAR(32),
    writable           BOOLEAN      NOT NULL DEFAULT FALSE,
    min_value          DOUBLE PRECISION,
    max_value          DOUBLE PRECISION,
    deadband           DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    alarm_low          DOUBLE PRECISION,
    alarm_high         DOUBLE PRECISION,
    interlock_rule_key VARCHAR(128),
    CONSTRAINT fk_tag_mappings_line
        FOREIGN KEY (site_id, line_id) REFERENCES lines (site_id, id),
    CONSTRAINT uq_tag_mappings_site_line_code
        UNIQUE (site_id, line_id, tag_code)
);

CREATE INDEX idx_tag_mappings_site_line ON tag_mappings (site_id, line_id);
