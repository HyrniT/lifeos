package com.lifeos.planning.repo;

import com.lifeos.planning.domain.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlanningUserSettingsRepository extends JpaRepository<UserSettings, UUID> {
}
