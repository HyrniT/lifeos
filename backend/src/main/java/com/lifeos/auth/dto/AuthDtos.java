package com.lifeos.auth.dto;

import com.lifeos.auth.domain.AuditLog;
import com.lifeos.auth.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Every request/response shape for this service, kept together so the API contract
 * can be read top to bottom without opening twenty files.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    // ------------------------------------------------------------- requests
    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,

            // Enforced here rather than only in the UI: 10+ chars with a letter and
            // a digit blocks the passwords that show up in every credential dump.
            @NotBlank
            @Size(min = 10, max = 128, message = "Password must be 10-128 characters")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                     message = "Password must contain at least one letter and one digit")
            String password,

            @NotBlank @Size(max = 120) String displayName,
            String timezone,
            String baseCurrency
    ) {
    }

    public record LoginRequest(
            /** An e-mail address, or a bare username for local accounts such as `admin`. */
            @NotBlank @Size(max = 255) String email,
            @NotBlank String password,
            /** Present on the second leg of a 2FA login. */
            String totpCode
    ) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record GoogleCallbackRequest(
            @NotBlank String code,
            @NotBlank String state,
            String redirectUri
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank
            @Size(min = 10, max = 128)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$")
            String newPassword
    ) {
    }

    public record UpdateProfileRequest(
            @Size(max = 120) String displayName,
            String avatarUrl,
            String locale,
            String timezone,
            @Size(min = 3, max = 3) String baseCurrency
    ) {
    }

    /** Second leg of the 2FA login: the challenge token issued by /login plus the code. */
    public record Verify2faRequest(
            @NotBlank String challengeToken,
            @NotBlank String code
    ) {
    }

    /** Used while enrolling in 2FA, where the user is already authenticated. */
    public record ConfirmTotpRequest(@NotBlank String code) {
    }

    public record AdminUpdateUserRequest(
            Boolean enabled,
            List<String> roles,
            String displayName
    ) {
    }

    // ------------------------------------------------------------ responses
    public record TokenPair(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            UserView user
    ) {
        public static TokenPair of(String access, String refresh, long ttl, UserView user) {
            return new TokenPair(access, refresh, "Bearer", ttl, user);
        }
    }

    /** Returned instead of a token pair when the account has 2FA switched on. */
    public record TwoFactorChallenge(
            boolean twoFactorRequired,
            String challengeToken,
            String message
    ) {
        public static TwoFactorChallenge of(String token) {
            return new TwoFactorChallenge(true, token,
                    "Enter the 6-digit code from your authenticator app");
        }
    }

    public record UserView(
            UUID id,
            String email,
            String displayName,
            String avatarUrl,
            String locale,
            String timezone,
            String baseCurrency,
            List<String> roles,
            boolean enabled,
            boolean emailVerified,
            boolean twoFactorEnabled,
            Instant lastLoginAt,
            Instant createdAt
    ) {
        public static UserView from(User u) {
            return new UserView(u.getId(), u.getEmail(), u.getDisplayName(), u.getAvatarUrl(),
                    u.getLocale(), u.getTimezone(), u.getBaseCurrency(), List.copyOf(u.getRoles()),
                    u.isEnabled(), u.isEmailVerified(), u.isTotpEnabled(),
                    u.getLastLoginAt(), u.getCreatedAt());
        }
    }

    public record GoogleAuthUrl(String authorizationUrl, String state) {
    }

    public record TotpSetup(String secret, String otpauthUri, List<String> recoveryCodes) {
    }

    public record SessionView(
            UUID id,
            String userAgent,
            String ipAddress,
            Instant issuedAt,
            Instant expiresAt,
            boolean current
    ) {
    }

    public record AuditView(
            UUID id,
            UUID userId,
            String userEmail,
            String action,
            String outcome,
            String detail,
            String ipAddress,
            Instant occurredAt
    ) {
        public static AuditView from(AuditLog a, String email) {
            return new AuditView(a.getId(), a.getUserId(), email, a.getAction(), a.getOutcome(),
                    a.getDetail(), a.getIpAddress(), a.getOccurredAt());
        }
    }

    public record AdminOverview(
            long totalUsers,
            long activeUsers,
            long disabledUsers,
            long newUsersLast7d,
            long activeSessions,
            long loginsLast24h,
            long failedLoginsLast24h,
            long googleLinkedAccounts,
            long twoFactorEnabledUsers,
            List<ActionCount> auditBreakdown
    ) {
    }

    public record ActionCount(String action, long count) {
    }

    public record MessageResponse(String message) {
        public static MessageResponse ok(String m) {
            return new MessageResponse(m);
        }
    }
}
