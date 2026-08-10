package com.lifeos.auth.service;

import com.lifeos.auth.domain.AuditLog;
import com.lifeos.auth.repo.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Runs in its own transaction on purpose: a failed login records the attempt and
     * then throws, and the audit row must survive the caller's rollback.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID userId, String action, String outcome, String detail, HttpServletRequest request) {
        repository.save(AuditLog.builder()
                .userId(userId)
                .action(action)
                .outcome(outcome)
                .detail(truncate(detail, 255))
                .ipAddress(clientIp(request))
                .userAgent(truncate(request == null ? null : request.getHeader("User-Agent"), 256))
                .occurredAt(Instant.now())
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void success(UUID userId, String action, String detail, HttpServletRequest request) {
        record(userId, action, "SUCCESS", detail, request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(UUID userId, String action, String detail, HttpServletRequest request) {
        record(userId, action, "FAILURE", detail, request);
    }

    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return (realIp != null && !realIp.isBlank()) ? realIp : request.getRemoteAddr();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
