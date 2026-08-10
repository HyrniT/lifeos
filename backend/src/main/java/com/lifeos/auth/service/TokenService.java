package com.lifeos.auth.service;

import com.lifeos.auth.domain.RefreshToken;
import com.lifeos.auth.domain.User;
import com.lifeos.auth.repo.RefreshTokenRepository;
import com.lifeos.common.exception.ApiException;
import com.lifeos.common.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Refresh-token lifecycle: issue, rotate, revoke, and detect reuse.
 *
 * Rotation means every refresh call burns the presented token and hands back a new
 * one. If a burnt token is ever presented again, either the client is buggy or the
 * token was stolen — we cannot tell which, so the entire family is revoked and the
 * user has to sign in again. That is the standard OAuth 2.1 recommendation.
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    private static final int RAW_TOKEN_BYTES = 48;

    private final RefreshTokenRepository repository;
    private final JwtService jwtService;
    private final SecureRandom random = new SecureRandom();

    public TokenService(RefreshTokenRepository repository, JwtService jwtService) {
        this.repository = repository;
        this.jwtService = jwtService;
    }

    public record IssuedTokens(String accessToken, String refreshToken, long expiresIn) {
    }

    @Transactional
    public IssuedTokens issueForNewSession(User user, HttpServletRequest request) {
        return issue(user, UUID.randomUUID(), request);
    }

    private IssuedTokens issue(User user, UUID familyId, HttpServletRequest request) {
        String access = jwtService.issueAccessToken(
                user.getId(), user.getEmail(), user.getDisplayName(), user.getRoles());

        String rawRefresh = randomToken();
        Instant now = Instant.now();

        repository.save(RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(sha256(rawRefresh))
                .familyId(familyId)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(jwtService.getRefreshTtlSeconds()))
                .userAgent(header(request, "User-Agent", 256))
                .ipAddress(AuditService.clientIp(request))
                .build());

        return new IssuedTokens(access, rawRefresh, jwtService.getAccessTtlSeconds());
    }

    /**
     * Consumes {@code presentedToken} and issues a replacement.
     *
     * @throws ApiException 401 when the token is unknown, expired or already used.
     */
    @Transactional
    public IssuedTokens rotate(String presentedToken, User user, HttpServletRequest request) {
        RefreshToken stored = repository.findByTokenHash(sha256(presentedToken))
                .orElseThrow(() -> ApiException.unauthorized("Refresh token is not recognised"));

        if (stored.getRevokedAt() != null) {
            // Already-used token replayed: assume compromise, kill the whole family.
            int revoked = repository.revokeFamily(stored.getFamilyId(), Instant.now());
            log.warn("Refresh-token reuse detected for user {} — revoked {} tokens in family {}",
                    stored.getUserId(), revoked, stored.getFamilyId());
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "TOKEN_REUSE_DETECTED",
                    "This session was invalidated for security reasons. Please sign in again.");
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("Refresh token has expired");
        }
        if (!stored.getUserId().equals(user.getId())) {
            throw ApiException.unauthorized("Refresh token does not belong to this user");
        }

        IssuedTokens fresh = issue(user, stored.getFamilyId(), request);

        stored.setRevokedAt(Instant.now());
        stored.setReplacedBy(sha256(fresh.refreshToken()));
        repository.save(stored);

        return fresh;
    }

    @Transactional
    public void revoke(String presentedToken) {
        repository.findByTokenHash(sha256(presentedToken)).ifPresent(t -> {
            t.setRevokedAt(Instant.now());
            repository.save(t);
        });
    }

    @Transactional
    public int revokeAllSessions(UUID userId) {
        return repository.revokeAllForUser(userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<RefreshToken> activeSessions(UUID userId) {
        return repository.findByUserIdAndRevokedAtIsNull(userId).stream()
                .filter(RefreshToken::isActive)
                .toList();
    }

    public UUID userIdOf(String presentedToken) {
        return repository.findByTokenHash(sha256(presentedToken))
                .map(RefreshToken::getUserId)
                .orElseThrow(() -> ApiException.unauthorized("Refresh token is not recognised"));
    }

    /** Removes tokens that expired more than a week ago; keeps the table small. */
    @Transactional
    public int purgeExpired() {
        return repository.deleteExpiredBefore(Instant.now().minusSeconds(7 * 24 * 3600));
    }

    // ---- helpers ---------------------------------------------------------
    private String randomToken() {
        byte[] buf = new byte[RAW_TOKEN_BYTES];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String header(HttpServletRequest request, String name, int max) {
        if (request == null) {
            return null;
        }
        String value = request.getHeader(name);
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
