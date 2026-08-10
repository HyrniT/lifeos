package com.lifeos;

import com.lifeos.platform.config.StartupChecks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A deployed instance must not start on the values printed in the README.
 *
 * No Spring context here on purpose: the point of the check is that it runs
 * before anything else does, and asserting on it directly is both faster and a
 * clearer statement of what is guaranteed.
 */
@DisplayName("A production start refuses the development defaults")
class StartupChecksTest {

    private static final String DOCUMENTED_DEFAULT =
            "change-me-in-production-this-must-be-at-least-64-bytes-long-for-hs512-algorithm";
    private static final String REAL_SECRET =
            "b7Qk2sYw9dLp4Rn8vTz1cHm6XjF3aG5eN0uK7iO2yB4tW9qZ8sD1fJ6hL3xC5vM0";

    @Test
    @DisplayName("the documented signing key is rejected by name")
    void refusesTheDocumentedSecret() {
        assertThatThrownBy(() -> check(DOCUMENTED_DEFAULT, false, "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("openssl rand");
    }

    @Test
    @DisplayName("a short key is rejected at startup, not on the first login")
    void refusesAShortSecret() {
        assertThatThrownBy(() -> check("too-short-for-hs512", false, "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("64 bytes");
    }

    @Test
    @DisplayName("seeding admin/admin into a public deployment is refused")
    void refusesTheDefaultAdminPassword() {
        assertThatThrownBy(() -> check(REAL_SECRET, true, "admin", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_PASSWORD");
    }

    @Test
    @DisplayName("a properly configured instance starts, with push merely off")
    void acceptsARealConfiguration() {
        assertThat(REAL_SECRET.getBytes().length).isGreaterThanOrEqualTo(64);

        assertThatCode(() -> check(REAL_SECRET, true, "a-real-password", "https://lifeos.vercel.app"))
                .doesNotThrowAnyException();
    }

    private void check(String secret, boolean seedAdmin, String adminPassword, String corsOrigin) {
        StartupChecks checks = new StartupChecks();
        ReflectionTestUtils.setField(checks, "jwtSecret", secret);
        ReflectionTestUtils.setField(checks, "adminSeedEnabled", seedAdmin);
        ReflectionTestUtils.setField(checks, "adminPassword", adminPassword);
        ReflectionTestUtils.setField(checks, "vapidPublicKey", "");
        ReflectionTestUtils.setField(checks, "corsOrigins", new String[]{corsOrigin});
        ReflectionTestUtils.invokeMethod(checks, "verify");
    }
}
