package com.lifeos.auth.repo;

import com.lifeos.auth.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * {@code CAST(:q AS String)} is required, not decorative: PostgreSQL types a
     * null string bind as {@code bytea}, and an unfiltered search would otherwise
     * fail with "function lower(bytea) does not exist".
     */
    @Query("""
            SELECT u FROM User u
            WHERE (:q IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:q AS String), '%'))
                              OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', CAST(:q AS String), '%')))
              AND (:enabled IS NULL OR u.enabled = :enabled)
            """)
    Page<User> search(@Param("q") String q, @Param("enabled") Boolean enabled, Pageable pageable);

    long countByEnabled(boolean enabled);

    long countByCreatedAtAfter(Instant since);

    long countByLastLoginAtAfter(Instant since);
}
