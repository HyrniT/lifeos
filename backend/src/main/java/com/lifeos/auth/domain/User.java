package com.lifeos.auth.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users", schema = "auth", indexes = {
        @Index(name = "idx_users_email", columnList = "email", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** Null for accounts that only ever signed in through Google. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(nullable = false, length = 8)
    @Builder.Default
    private String locale = "en";

    @Column(nullable = false, length = 64)
    @Builder.Default
    private String timezone = "UTC";

    @Column(name = "base_currency", nullable = false, length = 3)
    @Builder.Default
    private String baseCurrency = "USD";

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", schema = "auth", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 32)
    @Builder.Default
    private List<String> roles = new ArrayList<>(List.of("USER"));

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    // ---- two-factor ------------------------------------------------------
    @Column(name = "totp_secret", length = 64)
    private String totpSecret;

    @Column(name = "totp_enabled", nullable = false)
    @Builder.Default
    private boolean totpEnabled = false;

    /** Single-use recovery codes, stored as bcrypt hashes. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_recovery_codes", schema = "auth", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "code_hash", nullable = false, length = 100)
    @Builder.Default
    private List<String> recoveryCodeHashes = new ArrayList<>();

    // ---- audit -----------------------------------------------------------
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "last_login_ip", length = 64)
    private String lastLoginIp;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isAdmin() {
        return roles != null && roles.contains("ADMIN");
    }
}
