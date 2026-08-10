-- =====================================================================
--  Habit reminders
-- =====================================================================

-- Projection of the user's timezone, fed from lifeos.user.events. A habit
-- reminder set to "07:00" has to mean 07:00 where the user is; scheduling it from
-- the server clock puts it out by the whole UTC offset.
CREATE TABLE user_settings (
    user_id    UUID        PRIMARY KEY,
    timezone   VARCHAR(64) NOT NULL DEFAULT 'UTC',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- The scheduler scans "live habits that asked to be reminded" every few minutes,
-- so keep that set cheap rather than walking every habit ever created.
CREATE INDEX idx_habit_reminder_scan ON habit_view (reminder_time)
    WHERE archived = FALSE AND reminder_time IS NOT NULL;

CREATE INDEX idx_habit_streak_scan ON habit_view (current_streak)
    WHERE archived = FALSE;
