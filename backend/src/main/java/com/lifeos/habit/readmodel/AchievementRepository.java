package com.lifeos.habit.readmodel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AchievementRepository extends JpaRepository<UnlockedAchievement, UUID> {

    List<UnlockedAchievement> findByUserId(UUID userId);

    boolean existsByUserIdAndCode(UUID userId, String code);
}
