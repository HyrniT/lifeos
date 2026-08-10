-- =====================================================================
--  analytics: the CQRS query side
--
--  These two tables replace the MongoDB collections the microservice edition
--  used. The reason Mongo was there was the shape of `daily_rollup`: one
--  document per user per day with every domain's numbers pre-joined. Postgres
--  holds that shape perfectly well — the nested per-category map is the only
--  part that needed unnesting, and it becomes a child table.
-- =====================================================================

-- One row per user per day. Written by the projector, read by the charts.
CREATE TABLE daily_rollup (
    -- "<userId>:<date>" — the composite identity, kept as the key so a
    -- re-projection of the same day is an upsert rather than a duplicate.
    id                VARCHAR(64)   PRIMARY KEY,
    user_id           UUID          NOT NULL,
    rollup_date       DATE          NOT NULL,

    -- habits
    habit_check_ins   INT           NOT NULL DEFAULT 0,
    habits_due        INT           NOT NULL DEFAULT 0,
    xp_earned         INT           NOT NULL DEFAULT 0,
    best_streak       INT           NOT NULL DEFAULT 0,

    -- money
    expense_total     NUMERIC(19,4) NOT NULL DEFAULT 0,
    income_total      NUMERIC(19,4) NOT NULL DEFAULT 0,
    transaction_count INT           NOT NULL DEFAULT 0,

    -- planning
    tasks_completed   INT           NOT NULL DEFAULT 0,
    tasks_created     INT           NOT NULL DEFAULT 0,
    focus_minutes     INT           NOT NULL DEFAULT 0,
    focus_sessions    INT           NOT NULL DEFAULT 0,

    -- wellbeing
    mood              INT,
    energy            INT,

    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_rollup_user_date UNIQUE (user_id, rollup_date)
);
-- Every read is "this user, this date range, in order".
CREATE INDEX idx_rollup_user_date ON daily_rollup (user_id, rollup_date);

CREATE TABLE daily_rollup_category (
    rollup_id   VARCHAR(64)   NOT NULL REFERENCES daily_rollup (id) ON DELETE CASCADE,
    category_id VARCHAR(64)   NOT NULL,
    amount      NUMERIC(19,4) NOT NULL DEFAULT 0,
    PRIMARY KEY (rollup_id, category_id)
);

-- Raw archive of every event the projector consumed. Rollups answer today's
-- questions; this is what makes tomorrow's answerable without a backfill.
CREATE TABLE event_record (
    id             VARCHAR(64) PRIMARY KEY,
    user_id        UUID,
    event_type     VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(48),
    aggregate_id   VARCHAR(64),
    payload        JSONB,
    occurred_at    TIMESTAMPTZ NOT NULL,
    received_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_record_user_time ON event_record (user_id, occurred_at DESC);
CREATE INDEX idx_record_type_time ON event_record (event_type, occurred_at DESC);
-- Mongo expired these with a TTL index; here a scheduled delete does it, and this
-- index is what keeps that delete from scanning the whole table.
CREATE INDEX idx_record_occurred ON event_record (occurred_at);
