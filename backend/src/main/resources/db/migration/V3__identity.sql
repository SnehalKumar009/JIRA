-- Identity module (DOMAIN_MODEL §3.1). Authentication belongs to Keycloak; these tables hold the
-- app's own identity records and machine credentials (ADR-0002).

-- Users are created on first login and never deleted (D8, D10). Everything else refers to id.
CREATE TABLE app_user (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    idp_subject    text        NOT NULL UNIQUE,
    username       text        NOT NULL,
    display_name   text        NOT NULL,
    email          text,
    active         boolean     NOT NULL DEFAULT true,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    last_login_at  timestamptz,
    deactivated_at timestamptz,
    CONSTRAINT app_user_deactivation_consistent CHECK (active = (deactivated_at IS NULL))
);

CREATE INDEX app_user_username_idx ON app_user (lower(username));

-- Groups come from the identity provider (decision 7); membership is replaced on every login.
CREATE TABLE app_group (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    name       text        NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE group_membership (
    group_id uuid NOT NULL REFERENCES app_group (id),
    user_id  uuid NOT NULL REFERENCES app_user (id),
    PRIMARY KEY (group_id, user_id)
);

CREATE INDEX group_membership_user_idx ON group_membership (user_id);

-- Personal access tokens. Only a SHA-256 of the token is stored: tokens are 256-bit random values,
-- so a fast hash is safe and lets the token be looked up directly.
CREATE TABLE personal_access_token (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        uuid        NOT NULL REFERENCES app_user (id),
    name           text        NOT NULL,
    token_hash     bytea       NOT NULL UNIQUE,
    display_prefix text        NOT NULL,
    scopes         text[]      NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),
    expires_at     timestamptz NOT NULL,
    last_used_at   timestamptz,
    revoked_at     timestamptz,
    CONSTRAINT pat_name_length CHECK (char_length(name) BETWEEN 1 AND 100),
    CONSTRAINT pat_scopes_valid CHECK (cardinality(scopes) > 0
        AND scopes <@ ARRAY['read', 'write', 'admin']::text[]),
    CONSTRAINT pat_expiry_after_creation CHECK (expires_at > created_at)
);

CREATE INDEX personal_access_token_user_idx ON personal_access_token (user_id);

-- Links an app session to the Keycloak session that created it, so OIDC back-channel logout can
-- find and end it. Rows for sessions that no longer exist are swept periodically.
CREATE TABLE oidc_session (
    session_id text        PRIMARY KEY,
    issuer     text        NOT NULL,
    subject    text        NOT NULL,
    sid        text,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX oidc_session_sid_idx ON oidc_session (issuer, sid);
CREATE INDEX oidc_session_subject_idx ON oidc_session (issuer, subject);
