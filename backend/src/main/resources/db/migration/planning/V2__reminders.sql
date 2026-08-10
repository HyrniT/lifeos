-- =====================================================================
--  Deadline reminders
-- =====================================================================

-- Projection of the user's timezone, fed from lifeos.user.events. Reminders are
-- wall-clock events, so scheduling them from the server's clock is wrong by
-- however many hours the user's offset happens to be.
CREATE TABLE user_settings (
    user_id    UUID        PRIMARY KEY,
    timezone   VARCHAR(64) NOT NULL DEFAULT 'UTC',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- The old scheme could express exactly one reminder per task, on the due date
-- itself. Several reminders at different lead times cannot be tracked by a single
-- boolean, and idempotency now lives in notification-service's unique dedupe key,
-- which handles restarts and multiple replicas as well.
DROP INDEX IF EXISTS idx_task_reminder;
ALTER TABLE task DROP COLUMN IF EXISTS reminder_sent;

-- The scheduler's working set is "open tasks with a deadline in a window", so the
-- index has to lead with due_date rather than with the user.
CREATE INDEX idx_task_deadline_scan ON task (due_date, status)
    WHERE due_date IS NOT NULL;
