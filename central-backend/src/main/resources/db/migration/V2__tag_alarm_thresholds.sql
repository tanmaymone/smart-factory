ALTER TABLE tag_mappings
    ADD COLUMN IF NOT EXISTS warning_low DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS warning_high DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS critical_low DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS critical_high DOUBLE PRECISION;

UPDATE tag_mappings
SET critical_low = alarm_low
WHERE critical_low IS NULL;

UPDATE tag_mappings
SET critical_high = alarm_high
WHERE critical_high IS NULL;

ALTER TABLE tag_mappings
    DROP COLUMN IF EXISTS alarm_low,
    DROP COLUMN IF EXISTS alarm_high;
