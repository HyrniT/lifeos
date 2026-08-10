package com.lifeos.auth.service;

import com.lifeos.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import com.lifeos.platform.store.EphemeralStore;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Brute-force defence.
 *
 * Two independent counters run at once: one per account (stops a slow grind against
 * one victim) and one per source IP (stops a spray across many accounts). Either
 * tripping locks the attempt.
 *
 * The counters live in memory rather than in Redis, which is exactly right for a
 * single instance and would not be for several: two replicas would each allow the
 * full budget. If this ever runs more than once, the counters move to a table.
 */
@Service
public class LoginThrottleService {

    private static final Logger log = LoggerFactory.getLogger(LoginThrottleService.class);

    private static final String KEY_ACCOUNT = "lifeos:throttle:acct:";
    private static final String KEY_IP = "lifeos:throttle:ip:";

    private final EphemeralStore ephemeral;
    private final int maxAccountAttempts;
    private final int maxIpAttempts;
    private final Duration window;
    private final Duration lockout;

    public LoginThrottleService(
            EphemeralStore ephemeral,
            @Value("${lifeos.security.login.max-account-attempts:5}") int maxAccountAttempts,
            @Value("${lifeos.security.login.max-ip-attempts:20}") int maxIpAttempts,
            @Value("${lifeos.security.login.window-seconds:900}") long windowSeconds,
            @Value("${lifeos.security.login.lockout-seconds:900}") long lockoutSeconds) {
        this.ephemeral = ephemeral;
        this.maxAccountAttempts = maxAccountAttempts;
        this.maxIpAttempts = maxIpAttempts;
        this.window = Duration.ofSeconds(windowSeconds);
        this.lockout = Duration.ofSeconds(lockoutSeconds);
    }

    /** Throws 429 when the account or IP is currently locked out. */
    public void assertNotLocked(String email, String ip) {
        long acct = counter(KEY_ACCOUNT + normalise(email));
        long ipCount = counter(KEY_IP + ip);

        if (acct >= maxAccountAttempts) {
            log.warn("Login blocked: account {} exceeded {} attempts", normalise(email), maxAccountAttempts);
            throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "ACCOUNT_LOCKED",
                    "Too many failed attempts for this account. Try again in %d minutes."
                            .formatted(lockout.toMinutes()));
        }
        if (ipCount >= maxIpAttempts) {
            log.warn("Login blocked: ip {} exceeded {} attempts", ip, maxIpAttempts);
            throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "IP_THROTTLED",
                    "Too many failed attempts from this network. Try again later.");
        }
    }

    public void recordFailure(String email, String ip) {
        bump(KEY_ACCOUNT + normalise(email), maxAccountAttempts);
        bump(KEY_IP + ip, maxIpAttempts);
    }

    public void recordSuccess(String email, String ip) {
        ephemeral.remove(KEY_ACCOUNT + normalise(email));
        ephemeral.remove(KEY_IP + ip);
    }

    public int remainingAttempts(String email) {
        return Math.max(0, maxAccountAttempts - (int) counter(KEY_ACCOUNT + normalise(email)));
    }

    private void bump(String key, int threshold) {
        long value = ephemeral.increment(key, window);
        if (value >= threshold) {
            // Hitting the threshold extends the key to the full lockout duration.
            ephemeral.expire(key, lockout);
        }
    }

    private long counter(String key) {
        return ephemeral.get(key)
                .map(value -> {
                    try {
                        return Long.parseLong(value);
                    } catch (NumberFormatException ex) {
                        return 0L;
                    }
                })
                .orElse(0L);
    }

    private static String normalise(String email) {
        return email == null ? "unknown" : email.trim().toLowerCase();
    }
}
