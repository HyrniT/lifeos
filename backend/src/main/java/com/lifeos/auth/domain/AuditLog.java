package com.lifeos.auth.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Append-only security trail surfaced in the admin console. */
@Entity
@Table(name = "audit_log", schema = "auth", indexes = {
        @Index(name = "idx_audit_user_time", columnList = "user_id,occurred_at"),
        @Index(name = "idx_audit_action_time", columnList = "action,occurred_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(nullable = false, length = 16)
    @Builder.Default
    private String outcome = "SUCCESS";

    @Column(length = 255)
    private String detail;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 256)
    private String userAgent;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public static final class Action {
        public static final String LOGIN = "LOGIN";
        public static final String LOGIN_FAILED = "LOGIN_FAILED";
        public static final String LOGIN_LOCKED = "LOGIN_LOCKED";
        public static final String LOGOUT = "LOGOUT";
        public static final String REGISTER = "REGISTER";
        public static final String TOKEN_REFRESH = "TOKEN_REFRESH";
        public static final String TOKEN_REUSE_DETECTED = "TOKEN_REUSE_DETECTED";
        public static final String OAUTH_LOGIN = "OAUTH_LOGIN";
        public static final String OAUTH_LINKED = "OAUTH_LINKED";
        public static final String TWO_FA_ENABLED = "2FA_ENABLED";
        public static final String TWO_FA_DISABLED = "2FA_DISABLED";
        public static final String TWO_FA_FAILED = "2FA_FAILED";
        public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
        public static final String ADMIN_USER_UPDATED = "ADMIN_USER_UPDATED";
        public static final String ADMIN_USER_DISABLED = "ADMIN_USER_DISABLED";

        private Action() {
        }
    }
}
