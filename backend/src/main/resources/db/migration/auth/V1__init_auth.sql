-- =====================================================================
--  auth-service schema
-- =====================================================================

CREATE TABLE users (
    id                  UUID         PRIMARY KEY,
    email               VARCHAR(255) NOT NULL,
    password_hash       VARCHAR(100),
    display_name        VARCHAR(120) NOT NULL,
    avatar_url          VARCHAR(512),
    locale              VARCHAR(8)   NOT NULL DEFAULT 'en',
    timezone            VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    base_currency       VARCHAR(3)   NOT NULL DEFAULT 'USD',
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    email_verified      BOOLEAN      NOT NULL DEFAULT FALSE,
    totp_secret         VARCHAR(64),
    totp_enabled        BOOLEAN      NOT NULL DEFAULT FALSE,
    last_login_at       TIMESTAMPTZ,
    last_login_ip       VARCHAR(64),
    password_changed_at TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_users_email ON users (LOWER(email));
CREATE INDEX idx_users_created_at ON users (created_at);

CREATE TABLE user_roles (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(32) NOT NULL
);
CREATE INDEX idx_user_roles_user ON user_roles (user_id);

CREATE TABLE user_recovery_codes (
    user_id   UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash VARCHAR(100) NOT NULL
);
CREATE INDEX idx_recovery_user ON user_recovery_codes (user_id);

-- ---------------------------------------------------------------------
--  Refresh tokens: hashes only, with rotation families for reuse detection.
-- ---------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL,
    family_id   UUID        NOT NULL,
    issued_at   TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    replaced_by VARCHAR(64),
    user_agent  VARCHAR(256),
    ip_address  VARCHAR(64)
);
CREATE UNIQUE INDEX idx_refresh_hash   ON refresh_tokens (token_hash);
CREATE INDEX        idx_refresh_user   ON refresh_tokens (user_id);
CREATE INDEX        idx_refresh_family ON refresh_tokens (family_id);
-- Partial index: the hot query only ever looks at live tokens.
CREATE INDEX idx_refresh_active ON refresh_tokens (user_id, expires_at)
    WHERE revoked_at IS NULL;

CREATE TABLE oauth_accounts (
    id               UUID         PRIMARY KEY,
    user_id          UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider         VARCHAR(32)  NOT NULL,
    provider_subject VARCHAR(128) NOT NULL,
    provider_email   VARCHAR(255),
    linked_at        TIMESTAMPTZ  NOT NULL,
    last_used_at     TIMESTAMPTZ,
    CONSTRAINT uk_oauth_provider_subject UNIQUE (provider, provider_subject)
);
CREATE INDEX idx_oauth_user ON oauth_accounts (user_id);

CREATE TABLE audit_log (
    id          UUID         PRIMARY KEY,
    user_id     UUID,
    action      VARCHAR(64)  NOT NULL,
    outcome     VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS',
    detail      VARCHAR(255),
    ip_address  VARCHAR(64),
    user_agent  VARCHAR(256),
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_user_time   ON audit_log (user_id, occurred_at DESC);
CREATE INDEX idx_audit_action_time ON audit_log (action, occurred_at DESC);
