package com.lifeos.auth.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per issued refresh token.
 *
 * Only the SHA-256 hash is stored, so a database leak does not hand out live
 * sessions. Rotation links each token to its successor: if an already-rotated
 * token is presented again, that is token theft and the whole family is revoked.
 */
@Entity
@Table(name = "refresh_tokens", schema = "auth", indexes = {
        @Index(name = "idx_refresh_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_refresh_user", columnList = "user_id"),
        @Index(name = "idx_refresh_family", columnList = "family_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** All tokens descending from one login share a family id. */
    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by", length = 64)
    private String replacedBy;

    @Column(name = "user_agent", length = 256)
    private String userAgent;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    public boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }
}
