-- =====================================================================
--  habit-service: event store (write side) + projections (read side)
-- =====================================================================

-- ---------------------------------------------------------- write side
CREATE TABLE event_store (
    id             UUID        PRIMARY KEY,
    aggregate_id   UUID        NOT NULL,
    aggregate_type VARCHAR(48) NOT NULL,
    sequence_no    BIGINT      NOT NULL,
    event_type     VARCHAR(64) NOT NULL,
    user_id        UUID        NOT NULL,
    payload        JSONB       NOT NULL,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_event_aggregate_sequence UNIQUE (aggregate_id, sequence_no)
);
CREATE INDEX idx_event_aggregate ON event_store (aggregate_id, sequence_no);
CREATE INDEX idx_event_user_time ON event_store (user_id, occurred_at DESC);
CREATE INDEX idx_event_type      ON event_store (event_type);
-- Lets analytics-style questions ("all check-ins with mood >= 4") run without
-- adding a column every time a payload field becomes interesting.
CREATE INDEX idx_event_payload   ON event_store USING GIN (payload);

CREATE TABLE event_outbox (
    id           UUID         PRIMARY KEY,
    topic        VARCHAR(128) NOT NULL,
    event_type   VARCHAR(64)  NOT NULL,
    aggregate_id UUID         NOT NULL,
    user_id      UUID         NOT NULL,
    sequence_no  BIGINT       NOT NULL,
    payload      JSONB        NOT NULL,
    published    BOOLEAN      NOT NULL DEFAULT FALSE,
    attempts     INT          NOT NULL DEFAULT 0,
    last_error   VARCHAR(512),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ
);
-- Partial index: the relay only ever asks for unpublished rows.
CREATE INDEX idx_outbox_unpublished ON event_outbox (created_at) WHERE published = FALSE;

-- ----------------------------------------------------------- read side
CREATE TABLE habit_view (
    id                  UUID         PRIMARY KEY,
    user_id             UUID         NOT NULL,
    name                VARCHAR(120) NOT NULL,
    icon                VARCHAR(48)          DEFAULT 'target',
    color               VARCHAR(16)          DEFAULT '#111111',
    description         VARCHAR(500),
    type                VARCHAR(16)  NOT NULL DEFAULT 'BUILD',
    frequency           VARCHAR(24)  NOT NULL DEFAULT 'DAILY',
    interval_days       INT                  DEFAULT 1,
    target_per_period   INT          NOT NULL DEFAULT 1,
    unit                VARCHAR(16)  NOT NULL DEFAULT 'TIMES',
    unit_label          VARCHAR(24),
    target_value        DOUBLE PRECISION     DEFAULT 1.0,
    reminder_time       TIME,
    difficulty          VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
    category            VARCHAR(48)          DEFAULT 'general',
    sort_order          INT          NOT NULL DEFAULT 0,
    archived            BOOLEAN      NOT NULL DEFAULT FALSE,
    current_streak      INT          NOT NULL DEFAULT 0,
    longest_streak      INT          NOT NULL DEFAULT 0,
    total_check_ins     BIGINT       NOT NULL DEFAULT 0,
    last_check_in_date  DATE,
    completion_rate_30d DOUBLE PRECISION     DEFAULT 0.0,
    version_no          BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_habit_user         ON habit_view (user_id, archived, sort_order);
CREATE INDEX idx_habit_user_created ON habit_view (user_id, created_at);

CREATE TABLE habit_view_days (
    habit_id    UUID NOT NULL REFERENCES habit_view (id) ON DELETE CASCADE,
    day_of_week INT  NOT NULL
);
CREATE INDEX idx_habit_days ON habit_view_days (habit_id);

CREATE TABLE habit_log (
    id         UUID             PRIMARY KEY,
    habit_id   UUID             NOT NULL REFERENCES habit_view (id) ON DELETE CASCADE,
    user_id    UUID             NOT NULL,
    log_date   DATE             NOT NULL,
    value      DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    note       VARCHAR(500),
    mood       INT,
    xp_awarded INT              NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_log_habit_date UNIQUE (habit_id, log_date)
);
CREATE INDEX idx_log_user_date  ON habit_log (user_id, log_date DESC);
CREATE INDEX idx_log_habit_date ON habit_log (habit_id, log_date DESC);

CREATE TABLE user_stats (
    user_id            UUID        PRIMARY KEY,
    xp                 BIGINT      NOT NULL DEFAULT 0,
    level              INT         NOT NULL DEFAULT 1,
    coins              BIGINT      NOT NULL DEFAULT 0,
    hp                 INT         NOT NULL DEFAULT 100,
    total_check_ins    BIGINT      NOT NULL DEFAULT 0,
    current_day_streak INT         NOT NULL DEFAULT 0,
    longest_day_streak INT         NOT NULL DEFAULT 0,
    last_active_date   DATE,
    streak_freezes     INT         NOT NULL DEFAULT 1,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_stats_xp ON user_stats (xp DESC);

CREATE TABLE unlocked_achievement (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL,
    code        VARCHAR(64) NOT NULL,
    unlocked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_achievement_user_code UNIQUE (user_id, code)
);
CREATE INDEX idx_achievement_user ON unlocked_achievement (user_id);
