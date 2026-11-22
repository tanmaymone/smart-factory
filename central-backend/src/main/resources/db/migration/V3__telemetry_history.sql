CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;

CREATE TABLE telemetry_history (
    time           TIMESTAMPTZ       NOT NULL,
    site_id        VARCHAR(64)       NOT NULL,
    line_id        VARCHAR(64)       NOT NULL,
    tag_code       VARCHAR(128)      NOT NULL,
    numeric_value  DOUBLE PRECISION,
    boolean_value  BOOLEAN,
    string_value   VARCHAR(512),
    quality        VARCHAR(32)       NOT NULL
);

SELECT create_hypertable('telemetry_history', 'time');

CREATE INDEX idx_telemetry_history_tag_time
    ON telemetry_history (site_id, line_id, tag_code, time DESC);

CREATE INDEX idx_tag_mappings_external_address
    ON tag_mappings (site_id, line_id, external_address);
