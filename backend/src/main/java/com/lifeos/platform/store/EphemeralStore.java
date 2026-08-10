package com.lifeos.platform.store;

import java.time.Duration;
import java.util.Optional;

/**
 * What replaced Redis.
 *
 * Everything the application kept in Redis was short-lived and single-purpose: a
 * 2FA challenge that lives five minutes, a pending TOTP enrolment that lives
 * fifteen, an OAuth PKCE verifier that lives until the redirect comes back, and the
 * failed-login counters. None of it is worth a second piece of infrastructure at
 * this scale, and none of it is worth persisting — a restart invalidating a
 * half-finished login is a re-click, not data loss.
 *
 * @see InMemoryEphemeralStore
 */
public interface EphemeralStore {

    void put(String key, String value, Duration ttl);

    Optional<String> get(String key);

    void remove(String key);

    /**
     * Increments a counter, setting the TTL on first write.
     *
     * @return the value after the increment
     */
    long increment(String key, Duration ttl);

    /** Extends the life of an existing key; a no-op when the key is gone. */
    void expire(String key, Duration ttl);
}
