package com.lifeos.platform.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Refuses to start a deployed instance on the development defaults.
 *
 * Only under the {@code prod} profile: a local run is meant to work with no
 * configuration at all, and a check that fires there just teaches people to skip it.
 *
 * The two that throw are the ones where a default is an actual breach — a known
 * signing key lets anyone mint an admin token, and a seeded admin/admin account is
 * an open door. The rest are warnings about features that will simply be off.
 */
@Component
@Profile("prod")
public class StartupChecks {

    private static final Logger log = LoggerFactory.getLogger(StartupChecks.class);

    private static final String DEFAULT_SECRET =
            "change-me-in-production-this-must-be-at-least-64-bytes-long-for-hs512-algorithm";

    @Value("${lifeos.jwt.secret:}")
    private String jwtSecret;

    @Value("${lifeos.admin.enabled:false}")
    private boolean adminSeedEnabled;

    @Value("${lifeos.admin.password:}")
    private String adminPassword;

    @Value("${lifeos.push.vapid.public-key:}")
    private String vapidPublicKey;

    @Value("${lifeos.cors.allowed-origins:}")
    private String[] corsOrigins;

    @PostConstruct
    void verify() {
        if (DEFAULT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException("""
                    JWT_SECRET is still the documented default, so anyone who has read this \
                    repository can sign a token for any account. Generate one and set it:
                        openssl rand -base64 96""");
        }
        // HS512 needs 64 bytes of key; a shorter one makes jjwt throw on the first
        // login rather than at startup, which is a far worse place to find out.
        if (jwtSecret == null || jwtSecret.getBytes().length < 64) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least 64 bytes for HS512; it is currently "
                            + (jwtSecret == null ? 0 : jwtSecret.getBytes().length));
        }
        if (adminSeedEnabled && ("admin".equals(adminPassword) || adminPassword.isBlank())) {
            throw new IllegalStateException(
                    "The admin account is seeded with the default password. Set ADMIN_PASSWORD, "
                            + "or set ADMIN_SEED_ENABLED=false once you have an account.");
        }

        if (vapidPublicKey == null || vapidPublicKey.isBlank()) {
            log.warn("No VAPID key pair configured - browser push is off; in-app notifications still work.");
        }
        for (String origin : corsOrigins) {
            if (origin.contains("localhost")) {
                log.warn("CORS still allows {} - set LIFEOS_CORS_ORIGINS to the web app's real origin.",
                        origin.trim());
            }
        }
    }
}
