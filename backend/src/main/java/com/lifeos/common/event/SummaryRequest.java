package com.lifeos.common.event;

import java.time.LocalDate;
import java.util.UUID;

/**
 * "Build this user's daily summary."
 *
 * The notification package knows *when* each user wants their summary (it owns the
 * preference and the timezone); the planning package knows *what* is in it. Rather
 * than one reaching into the other's tables, this goes on the bus and whoever can
 * answer publishes a {@link ReminderMessage} back.
 *
 * The date doubles as the idempotency key downstream, so a duplicate request
 * produces no second notification.
 */
public record SummaryRequest(
        UUID userId,
        LocalDate localDate,
        String timezone
) {
}
