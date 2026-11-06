-- Audit module (DOMAIN_MODEL §3.6). Append-only: rows can be inserted, never changed or removed.
-- Actor and target are stored as IDs, not names, so anonymising a user needs no audit rewrite.
CREATE TABLE audit_event (
    id            bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at   timestamptz NOT NULL DEFAULT now(),
    actor_type    text        NOT NULL,
    actor_user_id uuid,
    actor_token_id uuid,
    action        text        NOT NULL,
    outcome       text        NOT NULL,
    target_type   text,
    target_id     text,
    details       jsonb       NOT NULL DEFAULT '{}'::jsonb,
    request_id    text,
    client_ip     inet,
    CONSTRAINT audit_event_actor_type CHECK (actor_type IN ('user', 'token', 'system', 'anonymous')),
    CONSTRAINT audit_event_outcome CHECK (outcome IN ('success', 'failure'))
);

CREATE INDEX audit_event_actor_idx ON audit_event (actor_user_id, id DESC);
CREATE INDEX audit_event_action_idx ON audit_event (action, id DESC);

CREATE FUNCTION audit_event_reject_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only (% rejected)', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER audit_event_no_update_or_delete
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION audit_event_reject_change();

CREATE TRIGGER audit_event_no_truncate
    BEFORE TRUNCATE ON audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION audit_event_reject_change();
