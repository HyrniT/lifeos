package com.lifeos.auth.repo;

import com.lifeos.auth.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:userId IS NULL OR a.userId = :userId)
              AND (:action IS NULL OR a.action = :action)
              AND (:outcome IS NULL OR a.outcome = :outcome)
              AND a.occurredAt >= :since
            ORDER BY a.occurredAt DESC
            """)
    Page<AuditLog> search(@Param("userId") UUID userId,
                          @Param("action") String action,
                          @Param("outcome") String outcome,
                          @Param("since") Instant since,
                          Pageable pageable);

    @Query("SELECT a.action, COUNT(a) FROM AuditLog a WHERE a.occurredAt >= :since GROUP BY a.action")
    List<Object[]> countByActionSince(@Param("since") Instant since);

    long countByActionAndOccurredAtAfter(String action, Instant since);
}
