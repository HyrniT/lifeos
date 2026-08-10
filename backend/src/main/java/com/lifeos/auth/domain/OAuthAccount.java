package com.lifeos.auth.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Links a LifeOS user to an external identity provider subject (Google's `sub`). */
@Entity
@Table(name = "oauth_accounts", schema = "auth", uniqueConstraints = {
        @UniqueConstraint(name = "uk_oauth_provider_subject", columnNames = {"provider", "provider_subject"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OAuthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "provider_subject", nullable = false, length = 128)
    private String providerSubject;

    @Column(name = "provider_email", length = 255)
    private String providerEmail;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;
}
