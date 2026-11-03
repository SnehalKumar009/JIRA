-- Installation-wide key/value metadata.
-- installation_id is generated exactly once; it changing means the database was lost or replaced.
CREATE TABLE system_metadata (
    key        text        PRIMARY KEY,
    value      text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO system_metadata (key, value) VALUES ('installation_id', gen_random_uuid()::text);
