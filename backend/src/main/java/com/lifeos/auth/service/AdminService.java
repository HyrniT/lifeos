package com.lifeos.auth.service;

import com.lifeos.auth.domain.AuditLog;
import com.lifeos.auth.domain.User;
import com.lifeos.auth.dto.AuthDtos.*;
import com.lifeos.auth.repo.AuditLogRepository;
import com.lifeos.auth.repo.OAuthAccountRepository;
import com.lifeos.auth.repo.RefreshTokenRepository;
import com.lifeos.auth.repo.UserRepository;
import com.lifeos.common.api.PageResponse;
import com.lifeos.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminService {

    private static final Set<String> ASSIGNABLE_ROLES = Set.of("USER", "ADMIN", "SUPPORT");

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final OAuthAccountRepository oauthAccounts;
    private final AuditLogRepository auditLogs;
    private final TokenService tokenService;
    private final AuditService audit;

    public AdminService(UserRepository users, RefreshTokenRepository refreshTokens,
                        OAuthAccountRepository oauthAccounts, AuditLogRepository auditLogs,
                        TokenService tokenService, AuditService audit) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.oauthAccounts = oauthAccounts;
        this.auditLogs = auditLogs;
        this.tokenService = tokenService;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserView> listUsers(String query, Boolean enabled, Pageable pageable) {
        String q = (query == null || query.isBlank()) ? null : query.trim();
        return PageResponse.from(users.search(q, enabled, pageable), UserView::from);
    }

    @Transactional(readOnly = true)
    public UserView getUser(UUID id) {
        return UserView.from(users.findById(id).orElseThrow(() -> ApiException.notFound("User", id)));
    }

    @Transactional
    public UserView updateUser(UUID id, AdminUpdateUserRequest req, UUID actingAdminId, HttpServletRequest http) {
        User user = users.findById(id).orElseThrow(() -> ApiException.notFound("User", id));

        if (req.roles() != null) {
            List<String> roles = req.roles().stream().map(String::toUpperCase).distinct().toList();
            for (String role : roles) {
                if (!ASSIGNABLE_ROLES.contains(role)) {
                    throw ApiException.badRequest("Unknown role: " + role);
                }
            }
            // Guard against an admin removing the last admin and locking everyone out.
            if (user.isAdmin() && !roles.contains("ADMIN") && countAdmins() <= 1) {
                throw ApiException.conflict("This is the only administrator — promote someone else first");
            }
            user.setRoles(new ArrayList<>(roles));
        }

        if (req.enabled() != null && req.enabled() != user.isEnabled()) {
            if (!req.enabled()) {
                if (id.equals(actingAdminId)) {
                    throw ApiException.badRequest("You cannot disable your own account");
                }
                if (user.isAdmin() && countAdmins() <= 1) {
                    throw ApiException.conflict("This is the only administrator");
                }
                // Disabling must end their sessions immediately, not at token expiry.
                tokenService.revokeAllSessions(id);
            }
            user.setEnabled(req.enabled());
            audit.success(actingAdminId,
                    req.enabled() ? AuditLog.Action.ADMIN_USER_UPDATED : AuditLog.Action.ADMIN_USER_DISABLED,
                    "target=" + user.getEmail(), http);
        }

        if (req.displayName() != null && !req.displayName().isBlank()) {
            user.setDisplayName(req.displayName().trim());
        }

        User saved = users.save(user);
        audit.success(actingAdminId, AuditLog.Action.ADMIN_USER_UPDATED, "target=" + saved.getEmail(), http);
        return UserView.from(saved);
    }

    @Transactional
    public int forceSignOut(UUID userId) {
        return tokenService.revokeAllSessions(userId);
    }

    @Transactional(readOnly = true)
    public AdminOverview overview() {
        Instant now = Instant.now();
        Instant last24h = now.minus(24, ChronoUnit.HOURS);
        Instant last7d = now.minus(7, ChronoUnit.DAYS);

        long total = users.count();
        long enabled = users.countByEnabled(true);

        List<ActionCount> breakdown = auditLogs.countByActionSince(last7d).stream()
                .map(row -> new ActionCount((String) row[0], ((Number) row[1]).longValue()))
                .sorted((a, b) -> Long.compare(b.count(), a.count()))
                .toList();

        return new AdminOverview(
                total,
                enabled,
                total - enabled,
                users.countByCreatedAtAfter(last7d),
                refreshTokens.countByRevokedAtIsNullAndExpiresAtAfter(now),
                auditLogs.countByActionAndOccurredAtAfter(AuditLog.Action.LOGIN, last24h),
                auditLogs.countByActionAndOccurredAtAfter(AuditLog.Action.LOGIN_FAILED, last24h),
                oauthAccounts.countByProvider("google"),
                users.findAll().stream().filter(User::isTotpEnabled).count(),
                breakdown);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditView> auditTrail(UUID userId, String action, String outcome,
                                              Instant since, Pageable pageable) {
        Instant from = since != null ? since : Instant.now().minus(30, ChronoUnit.DAYS);
        Page<AuditLog> page = auditLogs.search(userId, blankToNull(action), blankToNull(outcome), from, pageable);

        // One extra query resolves every e-mail on the page; per-row lookups here
        // would be N+1 against the audit table's busiest endpoint.
        Map<UUID, String> emails = new HashMap<>();
        List<UUID> ids = page.getContent().stream().map(AuditLog::getUserId).filter(java.util.Objects::nonNull)
                .distinct().toList();
        if (!ids.isEmpty()) {
            users.findAllById(ids).forEach(u -> emails.put(u.getId(), u.getEmail()));
        }

        return PageResponse.from(page, a -> AuditView.from(a, emails.get(a.getUserId())));
    }

    private long countAdmins() {
        return users.findAll().stream().filter(User::isAdmin).filter(User::isEnabled).count();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
