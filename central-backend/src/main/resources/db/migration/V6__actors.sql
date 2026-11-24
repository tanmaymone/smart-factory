CREATE TABLE actors (
    actor_id     VARCHAR(255) PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    role         VARCHAR(32)  NOT NULL,
    CONSTRAINT ck_actors_role CHECK (role IN ('VIEWER', 'OPERATOR', 'SUPERVISOR'))
);
