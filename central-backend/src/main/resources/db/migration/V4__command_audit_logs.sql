CREATE TABLE command_audit_logs (
    command_id       UUID         PRIMARY KEY,
    site_id          VARCHAR(64)  NOT NULL,
    line_id          VARCHAR(64)  NOT NULL,
    tag_code         VARCHAR(128) NOT NULL,
    requested_value  VARCHAR(255) NOT NULL,
    previous_value   VARCHAR(255),
    requested_by     VARCHAR(255) NOT NULL,
    reason           TEXT         NOT NULL,
    status           VARCHAR(32)  NOT NULL,
    rejection_reason TEXT,
    applied_value    VARCHAR(255),
    created_at       TIMESTAMPTZ  NOT NULL,
    acked_at         TIMESTAMPTZ
);

CREATE INDEX idx_command_audit_site_line_created
    ON command_audit_logs (site_id, line_id, created_at DESC);

CREATE INDEX idx_command_audit_tag_created
    ON command_audit_logs (site_id, line_id, tag_code, created_at DESC);

CREATE INDEX idx_command_audit_status_created
    ON command_audit_logs (status, created_at DESC);

CREATE OR REPLACE FUNCTION command_audit_logs_immutable_guard()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'command_audit_logs is append-only; DELETE is forbidden';
    END IF;

    IF NEW.command_id IS DISTINCT FROM OLD.command_id
        OR NEW.site_id IS DISTINCT FROM OLD.site_id
        OR NEW.line_id IS DISTINCT FROM OLD.line_id
        OR NEW.tag_code IS DISTINCT FROM OLD.tag_code
        OR NEW.requested_value IS DISTINCT FROM OLD.requested_value
        OR NEW.previous_value IS DISTINCT FROM OLD.previous_value
        OR NEW.requested_by IS DISTINCT FROM OLD.requested_by
        OR NEW.reason IS DISTINCT FROM OLD.reason
        OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'command_audit_logs identity columns are immutable';
    END IF;

    IF OLD.status = 'VALIDATED_DISPATCHED'
        AND NEW.status IN ('ACKED', 'FAILED_EDGE') THEN
        RETURN NEW;
    END IF;

    IF NEW.status IS DISTINCT FROM OLD.status
        OR NEW.rejection_reason IS DISTINCT FROM OLD.rejection_reason
        OR NEW.applied_value IS DISTINCT FROM OLD.applied_value
        OR NEW.acked_at IS DISTINCT FROM OLD.acked_at THEN
        RAISE EXCEPTION
            'command_audit_logs status may only transition from VALIDATED_DISPATCHED to ACKED or FAILED_EDGE';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_command_audit_logs_immutable
    BEFORE UPDATE OR DELETE ON command_audit_logs
    FOR EACH ROW
    EXECUTE FUNCTION command_audit_logs_immutable_guard();
