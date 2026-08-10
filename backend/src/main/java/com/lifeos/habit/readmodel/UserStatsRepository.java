package com.lifeos.habit.readmodel;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface UserStatsRepository extends JpaRepository<UserStats, UUID> {

    @Query("SELECT s FROM UserStats s ORDER BY s.xp DESC")
    List<UserStats> leaderboard(Pageable pageable);
}
