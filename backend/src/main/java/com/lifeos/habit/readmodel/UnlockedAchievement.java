package com.lifeos.habit.readmodel;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "unlocked_achievement", schema = "habit", uniqueConstraints = {
        @UniqueConstraint(name = "uk_achievement_user_code", columnNames = {"user_id", "code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnlockedAchievement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "unlocked_at", nullable = false)
    private Instant unlockedAt;
}
