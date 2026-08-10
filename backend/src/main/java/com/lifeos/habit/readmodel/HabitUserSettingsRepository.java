package com.lifeos.habit.readmodel;

import com.lifeos.habit.domain.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HabitUserSettingsRepository extends JpaRepository<UserSettings, UUID> {
}
