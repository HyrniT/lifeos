-- =====================================================================
--  planning-service schema
-- =====================================================================

CREATE TABLE project (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL,
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    icon        VARCHAR(48)           DEFAULT 'folder',
    color       VARCHAR(16)           DEFAULT '#111111',
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    due_date    DATE,
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_project_user ON project (user_id, status, sort_order);

CREATE TABLE goal (
    id            UUID          PRIMARY KEY,
    user_id       UUID          NOT NULL,
    title         VARCHAR(200)  NOT NULL,
    description   VARCHAR(2000),
    category      VARCHAR(48)            DEFAULT 'personal',
    icon          VARCHAR(48)            DEFAULT 'flag',
    color         VARCHAR(16)            DEFAULT '#111111',
    project_id    UUID          REFERENCES project (id) ON DELETE SET NULL,
    target_value  NUMERIC(19,4)          DEFAULT 1,
    current_value NUMERIC(19,4)          DEFAULT 0,
    unit          VARCHAR(32)            DEFAULT 'steps',
    start_date    DATE,
    target_date   DATE,
    status        VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    achieved_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_goal_user ON goal (user_id, status, target_date);

CREATE TABLE task (
    id               UUID         PRIMARY KEY,
    user_id          UUID         NOT NULL,
    title            VARCHAR(200) NOT NULL,
    notes            VARCHAR(2000),
    project_id       UUID         REFERENCES project (id) ON DELETE SET NULL,
    goal_id          UUID         REFERENCES goal (id)    ON DELETE SET NULL,
    parent_task_id   UUID,
    priority         VARCHAR(8)   NOT NULL DEFAULT 'P3',
    status           VARCHAR(16)  NOT NULL DEFAULT 'TODO',
    due_date         DATE,
    due_time         TIME,
    scheduled_for    DATE,
    estimate_minutes INT,
    actual_minutes   INT          NOT NULL DEFAULT 0,
    recurrence       VARCHAR(16)  NOT NULL DEFAULT 'NONE',
    sort_order       INT          NOT NULL DEFAULT 0,
    completed_at     TIMESTAMPTZ,
    reminder_sent    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_task_user_status ON task (user_id, status, due_date);
CREATE INDEX idx_task_user_due    ON task (user_id, due_date);
CREATE INDEX idx_task_project     ON task (project_id);
CREATE INDEX idx_task_parent      ON task (parent_task_id);
-- The reminder scheduler scans only unsent, still-open tasks.
CREATE INDEX idx_task_reminder ON task (due_date) WHERE reminder_sent = FALSE;

CREATE TABLE task_tags (
    task_id UUID        NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    tag     VARCHAR(40) NOT NULL
);
CREATE INDEX idx_task_tags ON task_tags (task_id);

CREATE TABLE focus_session (
    id              UUID        PRIMARY KEY,
    user_id         UUID        NOT NULL,
    task_id         UUID        REFERENCES task (id) ON DELETE SET NULL,
    type            VARCHAR(16) NOT NULL DEFAULT 'POMODORO',
    session_date    DATE        NOT NULL,
    started_at      TIMESTAMPTZ NOT NULL,
    ended_at        TIMESTAMPTZ,
    planned_minutes INT         NOT NULL DEFAULT 25,
    actual_minutes  INT         NOT NULL DEFAULT 0,
    completed       BOOLEAN     NOT NULL DEFAULT FALSE,
    focus_score     INT,
    note            VARCHAR(500)
);
CREATE INDEX idx_focus_user_date ON focus_session (user_id, session_date DESC);
CREATE INDEX idx_focus_task      ON focus_session (task_id);

CREATE TABLE journal_entry (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL,
    entry_date DATE        NOT NULL,
    mood       INT,
    energy     INT,
    highlights VARCHAR(1000),
    gratitude  VARCHAR(1000),
    notes      VARCHAR(4000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_journal_user_date UNIQUE (user_id, entry_date)
);
CREATE INDEX idx_journal_user ON journal_entry (user_id, entry_date DESC);
