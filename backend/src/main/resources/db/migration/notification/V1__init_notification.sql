-- =====================================================================
--  notification-service schema
-- =====================================================================

-- Local projection of the user profile, fed from lifeos.user.events. Exists so
-- quiet hours and reminder times are evaluated in the user's own timezone
-- without a synchronous call to auth-service on a background job's hot path.
CREATE TABLE user_settings (
    user_id      UUID        PRIMARY KEY,
    email        VARCHAR(255),
    display_name VARCHAR(120),
    timezone     VARCHAR(64) NOT NULL DEFAULT 'UTC',
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE notification_preference (
    user_id               UUID        PRIMARY KEY,
    in_app_enabled        BOOLEAN     NOT NULL DEFAULT TRUE,
    push_enabled          BOOLEAN     NOT NULL DEFAULT TRUE,
    email_enabled         BOOLEAN     NOT NULL DEFAULT FALSE,
    remind_at_deadline    BOOLEAN     NOT NULL DEFAULT TRUE,
    remind_when_overdue   BOOLEAN     NOT NULL DEFAULT TRUE,
    daily_summary_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    daily_summary_time    TIME        NOT NULL DEFAULT '08:00',
    quiet_hours_enabled   BOOLEAN     NOT NULL DEFAULT TRUE,
    quiet_from            TIME        NOT NULL DEFAULT '22:00',
    quiet_to              TIME        NOT NULL DEFAULT '07:00',
    timezone              VARCHAR(64) NOT NULL DEFAULT 'UTC',
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE notification_muted_kind (
    user_id UUID        NOT NULL REFERENCES notification_preference (user_id) ON DELETE CASCADE,
    kind    VARCHAR(48) NOT NULL
);
CREATE INDEX idx_muted_kind_user ON notification_muted_kind (user_id);

-- Minutes before a deadline to warn. Several rows per user means several warnings.
CREATE TABLE notification_lead_time (
    user_id        UUID NOT NULL REFERENCES notification_preference (user_id) ON DELETE CASCADE,
    minutes_before INT  NOT NULL CHECK (minutes_before > 0)
);
CREATE INDEX idx_lead_time_user ON notification_lead_time (user_id);

CREATE TABLE push_subscription (
    id              UUID          PRIMARY KEY,
    user_id         UUID          NOT NULL,
    endpoint        VARCHAR(1024) NOT NULL,
    p256dh          VARCHAR(255)  NOT NULL,
    auth            VARCHAR(255)  NOT NULL,
    user_agent      VARCHAR(256),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    last_success_at TIMESTAMPTZ,
    failure_count   INT           NOT NULL DEFAULT 0
);
CREATE INDEX idx_push_user ON push_subscription (user_id);
-- The endpoint identifies a browser, so re-subscribing updates rather than
-- duplicating. Hashed because the column is too long for a plain btree index.
CREATE UNIQUE INDEX uk_push_endpoint ON push_subscription (md5(endpoint));

CREATE TABLE notification (
    id            UUID         PRIMARY KEY,
    user_id       UUID         NOT NULL,
    kind          VARCHAR(48)  NOT NULL,
    title         VARCHAR(160) NOT NULL,
    body          VARCHAR(500),
    icon          VARCHAR(48)           DEFAULT 'bell',
    severity      VARCHAR(16)  NOT NULL DEFAULT 'info',
    deep_link     VARCHAR(256),
    dedupe_key    VARCHAR(200) NOT NULL,
    data          JSONB,
    is_read       BOOLEAN      NOT NULL DEFAULT FALSE,
    deliver_after TIMESTAMPTZ,
    delivered     BOOLEAN      NOT NULL DEFAULT FALSE,
    delivered_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- The idempotency guarantee. Schedulers fire on an interval, across replicas, and
-- replay their window after a restart, so the same reminder is produced more than
-- once by design; the second insert simply loses.
CREATE UNIQUE INDEX uk_notification_dedupe ON notification (dedupe_key);
CREATE INDEX idx_notification_user ON notification (user_id, created_at DESC);
-- Partial: the dispatcher only ever asks for undelivered rows.
CREATE INDEX idx_notification_pending ON notification (deliver_after) WHERE delivered = FALSE;
CREATE INDEX idx_notification_unread ON notification (user_id) WHERE is_read = FALSE;
