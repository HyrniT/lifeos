package com.lifeos.notification.repo;

import com.lifeos.notification.domain.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationUserSettingsRepository extends JpaRepository<UserSettings, UUID> {
}
