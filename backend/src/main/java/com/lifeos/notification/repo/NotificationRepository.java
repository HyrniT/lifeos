package com.lifeos.notification.repo;

import com.lifeos.notification.domain.StoredNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<StoredNotification, UUID> {

    boolean existsByDedupeKey(String dedupeKey);

    Optional<StoredNotification> findByIdAndUserId(UUID id, UUID userId);

    @Query("""
            SELECT n FROM StoredNotification n
            WHERE n.userId = :userId
              AND (:unreadOnly = false OR n.read = false)
              AND (n.deliverAfter IS NULL OR n.deliverAfter <= :now)
            ORDER BY n.createdAt DESC
            """)
    List<StoredNotification> inbox(@Param("userId") UUID userId,
                                   @Param("unreadOnly") boolean unreadOnly,
                                   @Param("now") Instant now,
                                   Pageable pageable);

    @Query("""
            SELECT COUNT(n) FROM StoredNotification n
            WHERE n.userId = :userId AND n.read = false
              AND (n.deliverAfter IS NULL OR n.deliverAfter <= :now)
            """)
    long unreadCount(@Param("userId") UUID userId, @Param("now") Instant now);

    /** Deferred by quiet hours and now due for release. */
    @Query("""
            SELECT n FROM StoredNotification n
            WHERE n.delivered = false
              AND (n.deliverAfter IS NULL OR n.deliverAfter <= :now)
            ORDER BY n.createdAt ASC
            """)
    List<StoredNotification> pendingDelivery(@Param("now") Instant now, Pageable pageable);

    @Modifying
    @Query("UPDATE StoredNotification n SET n.read = true WHERE n.userId = :userId AND n.read = false")
    int markAllRead(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM StoredNotification n WHERE n.userId = :userId")
    int deleteAllForUser(@Param("userId") UUID userId);

    /** Housekeeping: an unread notification from three months ago is not news. */
    @Modifying
    @Query("DELETE FROM StoredNotification n WHERE n.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
