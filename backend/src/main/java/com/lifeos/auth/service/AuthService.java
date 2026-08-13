package com.lifeos.auth.service;

import com.lifeos.auth.domain.AuditLog;
import com.lifeos.auth.domain.OAuthAccount;
import com.lifeos.auth.domain.User;
import com.lifeos.auth.dto.AuthDtos.*;
import com.lifeos.auth.repo.OAuthAccountRepository;
import com.lifeos.auth.repo.UserRepository;
import com.lifeos.common.event.DomainEvent;
import com.lifeos.common.event.EventPublisher;
import com.lifeos.common.event.Topics;
import com.lifeos.common.api.Money;
import com.lifeos.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.lifeos.platform.store.EphemeralStore;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String CHALLENGE_KEY = "lifeos:2fa:challenge:";
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final int RECOVERY_CODE_COUNT = 10;

    private final UserRepository users;
    private final OAuthAccountRepository oauthAccounts;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final TotpService totpService;
    private final LoginThrottleService throttle;
    private final AuditService audit;
    private final GoogleOAuthService google;
    private final EphemeralStore ephemeral;
    private final EventPublisher events;
    private final SecureRandom random = new SecureRandom();

    /**
     * A real bcrypt hash of a throwaway value, computed once at startup. Comparing
     * against it for unknown accounts costs the same as a genuine check, which is
     * what keeps login timing from leaking whether an address is registered.
     */
    private final String decoyHash;

    public AuthService(UserRepository users, OAuthAccountRepository oauthAccounts,
                       PasswordEncoder passwordEncoder, TokenService tokenService,
                       TotpService totpService, LoginThrottleService throttle,
                       AuditService audit, GoogleOAuthService google,
                       EphemeralStore ephemeral, EventPublisher events) {
        this.users = users;
        this.oauthAccounts = oauthAccounts;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.totpService = totpService;
        this.throttle = throttle;
        this.audit = audit;
        this.google = google;
        this.ephemeral = ephemeral;
        this.events = events;

        byte[] filler = new byte[24];
        this.random.nextBytes(filler);
        this.decoyHash = passwordEncoder.encode(Base64.getEncoder().encodeToString(filler));
    }

    // ================================================================ register
    @Transactional
    public TokenPair register(RegisterRequest req, HttpServletRequest http) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            // Deliberately explicit: hiding this only pushes the enumeration to the
            // password-reset flow, and the UX cost of a vague error is real.
            throw ApiException.conflict("An account already exists for " + email);
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(req.password()))
                .displayName(req.displayName().trim())
                .timezone(validZone(blankTo(req.timezone(), "UTC").trim()))
                .baseCurrency(Money.BASE_CURRENCY)
                .roles(new ArrayList<>(List.of("USER")))
                .passwordChangedAt(Instant.now())
                .build();
        user = users.save(user);

        audit.success(user.getId(), AuditLog.Action.REGISTER, email, http);
        publishUserEvent(Topics.User.REGISTERED, user);

        var issued = tokenService.issueForNewSession(user, http);
        touchLogin(user, http);
        return TokenPair.of(issued.accessToken(), issued.refreshToken(), issued.expiresIn(), UserView.from(user));
    }

    // =================================================================== login
    /** @return either a {@link TokenPair} or a {@link TwoFactorChallenge}. */
    @Transactional
    public Object login(LoginRequest req, HttpServletRequest http) {
        String email = normaliseLogin(req.email());
        String ip = AuditService.clientIp(http);

        throttle.assertNotLocked(email, ip);

        User user = users.findByEmailIgnoreCase(email).orElse(null);

        // Run the hash comparison even for unknown accounts so the response time does
        // not reveal whether the address exists.
        boolean passwordOk = user != null
                && user.getPasswordHash() != null
                && passwordEncoder.matches(req.password(), user.getPasswordHash());
        if (user == null || user.getPasswordHash() == null) {
            passwordEncoder.matches(req.password(), decoyHash);
        }

        if (!passwordOk) {
            throttle.recordFailure(email, ip);
            audit.failure(user == null ? null : user.getId(), AuditLog.Action.LOGIN_FAILED, email, http);
            int left = throttle.remainingAttempts(email);
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    left > 0
                            ? "Incorrect e-mail or password. %d attempt(s) left before a temporary lock."
                                    .formatted(left)
                            : "Incorrect e-mail or password.");
        }
        if (!user.isEnabled()) {
            audit.failure(user.getId(), AuditLog.Action.LOGIN_FAILED, "account disabled", http);
            throw ApiException.forbidden("This account has been disabled. Contact an administrator.");
        }

        if (user.isTotpEnabled()) {
            if (req.totpCode() == null || req.totpCode().isBlank()) {
                return TwoFactorChallenge.of(createChallenge(user.getId()));
            }
            if (!totpService.verify(user.getTotpSecret(), req.totpCode())) {
                throttle.recordFailure(email, ip);
                audit.failure(user.getId(), AuditLog.Action.TWO_FA_FAILED, null, http);
                throw ApiException.unauthorized("That verification code is not valid");
            }
        }

        throttle.recordSuccess(email, ip);
        return completeLogin(user, http, AuditLog.Action.LOGIN);
    }

    /** Second leg of the 2FA login. */
    @Transactional
    public TokenPair verifyTwoFactor(Verify2faRequest req, HttpServletRequest http) {
        String userIdRaw = ephemeral.get(CHALLENGE_KEY + req.challengeToken()).orElse(null);
        if (userIdRaw == null) {
            throw ApiException.unauthorized("This sign-in attempt expired. Please start again.");
        }
        UUID userId = UUID.fromString(userIdRaw);
        User user = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));

        String ip = AuditService.clientIp(http);
        throttle.assertNotLocked(user.getEmail(), ip);

        boolean ok = totpService.verify(user.getTotpSecret(), req.code())
                || consumeRecoveryCode(user, req.code());
        if (!ok) {
            throttle.recordFailure(user.getEmail(), ip);
            audit.failure(user.getId(), AuditLog.Action.TWO_FA_FAILED, null, http);
            throw ApiException.unauthorized("That verification code is not valid");
        }

        ephemeral.remove(CHALLENGE_KEY + req.challengeToken());
        throttle.recordSuccess(user.getEmail(), ip);
        return completeLogin(user, http, AuditLog.Action.LOGIN);
    }

    // ============================================================ google login
    @Transactional
    public TokenPair loginWithGoogle(GoogleCallbackRequest req, HttpServletRequest http) {
        var profile = google.exchangeCode(req.code(), req.state(), req.redirectUri());

        if (profile.email() == null || !profile.emailVerified()) {
            throw ApiException.badRequest("Your Google account must have a verified e-mail address");
        }

        OAuthAccount link = oauthAccounts
                .findByProviderAndProviderSubject("google", profile.subject())
                .orElse(null);

        User user;
        if (link != null) {
            user = users.findById(link.getUserId())
                    .orElseThrow(() -> ApiException.unauthorized("Linked account no longer exists"));
            link.setLastUsedAt(Instant.now());
            oauthAccounts.save(link);
        } else {
            String email = profile.email().trim().toLowerCase();
            user = users.findByEmailIgnoreCase(email).orElse(null);

            if (user == null) {
                // First Google sign-in creates the account. No password is set, so
                // the only way in stays the provider until the user adds one.
                user = users.save(User.builder()
                        .email(email)
                        .passwordHash(null)
                        .displayName(profile.name() != null ? profile.name() : email)
                        .avatarUrl(profile.picture())
                        .emailVerified(true)
                        .roles(new ArrayList<>(List.of("USER")))
                        .build());
                audit.success(user.getId(), AuditLog.Action.REGISTER, "via google", http);
                publishUserEvent(Topics.User.REGISTERED, user);
            } else if (!user.isEmailVerified()) {
                // Google has verified the address, so we can trust it now.
                user.setEmailVerified(true);
                if (user.getAvatarUrl() == null) {
                    user.setAvatarUrl(profile.picture());
                }
                users.save(user);
            }

            oauthAccounts.save(OAuthAccount.builder()
                    .userId(user.getId())
                    .provider("google")
                    .providerSubject(profile.subject())
                    .providerEmail(profile.email())
                    .linkedAt(Instant.now())
                    .lastUsedAt(Instant.now())
                    .build());
            audit.success(user.getId(), AuditLog.Action.OAUTH_LINKED, "google", http);
        }

        if (!user.isEnabled()) {
            throw ApiException.forbidden("This account has been disabled.");
        }
        return completeLogin(user, http, AuditLog.Action.OAUTH_LOGIN);
    }

    // ================================================================= tokens
    @Transactional
    public TokenPair refresh(String refreshToken, HttpServletRequest http) {
        UUID userId = tokenService.userIdOf(refreshToken);
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
        if (!user.isEnabled()) {
            tokenService.revokeAllSessions(userId);
            throw ApiException.forbidden("This account has been disabled.");
        }

        var issued = tokenService.rotate(refreshToken, user, http);
        audit.success(userId, AuditLog.Action.TOKEN_REFRESH, null, http);
        return TokenPair.of(issued.accessToken(), issued.refreshToken(), issued.expiresIn(), UserView.from(user));
    }

    @Transactional
    public void logout(String refreshToken, UUID userId, HttpServletRequest http) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            tokenService.revoke(refreshToken);
        }
        audit.success(userId, AuditLog.Action.LOGOUT, null, http);
    }

    // =========================================================== two-factor
    @Transactional
    public TotpSetup beginTotpEnrollment(UUID userId) {
        User user = require(userId);
        if (user.isTotpEnabled()) {
            throw ApiException.conflict("Two-factor authentication is already switched on");
        }
        String secret = totpService.generateSecret();
        // Held outside the database until confirmed, so an abandoned enrolment leaves no trace.
        ephemeral.put(pendingTotpKey(userId), secret, Duration.ofMinutes(15));

        List<String> recoveryCodes = totpService.generateRecoveryCodes(RECOVERY_CODE_COUNT);
        ephemeral.put(pendingRecoveryKey(userId), String.join(",", recoveryCodes),
                Duration.ofMinutes(15));

        return new TotpSetup(secret, totpService.buildOtpAuthUri(secret, user.getEmail()), recoveryCodes);
    }

    @Transactional
    public List<String> confirmTotpEnrollment(UUID userId, String code, HttpServletRequest http) {
        String secret = ephemeral.get(pendingTotpKey(userId)).orElse(null);
        if (secret == null) {
            throw ApiException.badRequest("Enrolment expired — start setting up two-factor again");
        }
        if (!totpService.verify(secret, code)) {
            throw ApiException.badRequest("That code did not match. Check your authenticator and try again.");
        }

        String storedCodes = ephemeral.get(pendingRecoveryKey(userId)).orElse(null);
        List<String> recoveryCodes = storedCodes == null ? List.of() : List.of(storedCodes.split(","));

        User user = require(userId);
        user.setTotpSecret(secret);
        user.setTotpEnabled(true);
        user.setRecoveryCodeHashes(new ArrayList<>(recoveryCodes.stream().map(passwordEncoder::encode).toList()));
        users.save(user);

        ephemeral.remove(pendingTotpKey(userId));
        ephemeral.remove(pendingRecoveryKey(userId));
        audit.success(userId, AuditLog.Action.TWO_FA_ENABLED, null, http);
        return recoveryCodes;
    }

    @Transactional
    public void disableTotp(UUID userId, String currentPassword, HttpServletRequest http) {
        User user = require(userId);
        if (user.getPasswordHash() != null
                && !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.unauthorized("Current password is incorrect");
        }
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        user.getRecoveryCodeHashes().clear();
        users.save(user);
        audit.success(userId, AuditLog.Action.TWO_FA_DISABLED, null, http);
    }

    // ================================================================ profile
    @Transactional(readOnly = true)
    public UserView me(UUID userId) {
        return UserView.from(require(userId));
    }

    @Transactional
    public UserView updateProfile(UUID userId, UpdateProfileRequest req) {
        User user = require(userId);
        if (req.displayName() != null && !req.displayName().isBlank()) {
            user.setDisplayName(req.displayName().trim());
        }
        if (req.avatarUrl() != null) {
            user.setAvatarUrl(req.avatarUrl());
        }
        if (req.locale() != null && !req.locale().isBlank()) {
            user.setLocale(req.locale());
        }
        if (req.timezone() != null && !req.timezone().isBlank()) {
            user.setTimezone(validZone(req.timezone().trim()));
        }
        // baseCurrency is intentionally not settable — see com.lifeos.common.api.Money.
        // An older client may still send it; the field is normalised rather than
        // rejected so such a request succeeds and simply changes nothing.
        user.setBaseCurrency(Money.BASE_CURRENCY);
        User saved = users.save(user);

        // Other services keep a local projection of the timezone so their reminder
        // schedulers fire in the user's wall-clock time. Without this event, moving
        // country would leave every reminder in the old zone indefinitely.
        publishUserEvent(Topics.User.PROFILE_UPDATED, saved);
        return UserView.from(saved);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest req, HttpServletRequest http) {
        User user = require(userId);
        if (user.getPasswordHash() == null) {
            // Google-only account adding its first password: nothing to verify.
            user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        } else {
            if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
                audit.failure(userId, AuditLog.Action.PASSWORD_CHANGED, "wrong current password", http);
                throw ApiException.unauthorized("Current password is incorrect");
            }
            if (passwordEncoder.matches(req.newPassword(), user.getPasswordHash())) {
                throw ApiException.badRequest("The new password must differ from the current one");
            }
            user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        }
        user.setPasswordChangedAt(Instant.now());
        users.save(user);

        // Changing a password ends every other session — that is the whole point of
        // changing it after a suspected compromise.
        tokenService.revokeAllSessions(userId);
        audit.success(userId, AuditLog.Action.PASSWORD_CHANGED, null, http);
    }

    @Transactional(readOnly = true)
    public List<SessionView> sessions(UUID userId) {
        return tokenService.activeSessions(userId).stream()
                .map(t -> new SessionView(t.getId(), t.getUserAgent(), t.getIpAddress(),
                        t.getIssuedAt(), t.getExpiresAt(), false))
                .toList();
    }

    @Transactional
    public int revokeAllSessions(UUID userId) {
        return tokenService.revokeAllSessions(userId);
    }

    // ================================================================ helpers
    private TokenPair completeLogin(User user, HttpServletRequest http, String action) {
        var issued = tokenService.issueForNewSession(user, http);
        touchLogin(user, http);
        audit.success(user.getId(), action, null, http);
        publishUserEvent(Topics.User.LOGGED_IN, user);
        return TokenPair.of(issued.accessToken(), issued.refreshToken(), issued.expiresIn(), UserView.from(user));
    }

    private void touchLogin(User user, HttpServletRequest http) {
        user.setLastLoginAt(Instant.now());
        user.setLastLoginIp(AuditService.clientIp(http));
        users.save(user);
    }

    private String createChallenge(UUID userId) {
        byte[] buf = new byte[32];
        random.nextBytes(buf);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        ephemeral.put(CHALLENGE_KEY + token, userId.toString(), CHALLENGE_TTL);
        return token;
    }

    private boolean consumeRecoveryCode(User user, String code) {
        List<String> hashes = user.getRecoveryCodeHashes();
        for (int i = 0; i < hashes.size(); i++) {
            if (passwordEncoder.matches(code.trim().toUpperCase(), hashes.get(i))) {
                hashes.remove(i);           // single use
                users.save(user);
                log.info("Recovery code consumed for user {} ({} left)", user.getId(), hashes.size());
                return true;
            }
        }
        return false;
    }

    private void publishUserEvent(String type, User user) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", user.getEmail());
        payload.put("displayName", user.getDisplayName());
        payload.put("timezone", user.getTimezone());
        payload.put("baseCurrency", user.getBaseCurrency());
        events.publish(Topics.USER_EVENTS,
                DomainEvent.of(type, "User", user.getId().toString(), user.getId(), 0L, payload));
    }

    /**
     * Rejects a timezone this system cannot act on, instead of storing it.
     *
     * Storing it is the tempting option and the wrong one. Downstream, each
     * service treats an unparseable zone differently — habit and planning keep
     * the *previous* zone and log a warning, notification silently falls back to
     * UTC — so one bad string leaves the same account on three different clocks
     * while Settings cheerfully displays the value the user typed. Failing the
     * write is the only outcome the user can see and correct.
     */
    private static String validZone(String timezone) {
        try {
            return ZoneId.of(timezone).getId();
        } catch (DateTimeException ex) {
            throw ApiException.badRequest(
                    "'" + timezone + "' is not a known time zone. Use an IANA identifier such as Europe/Zurich.");
        }
    }

    private User require(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("User", userId));
    }

    private static String pendingTotpKey(UUID userId) {
        return "lifeos:2fa:pending:" + userId;
    }

    private static String pendingRecoveryKey(UUID userId) {
        return "lifeos:2fa:recovery:" + userId;
    }

    private static String blankTo(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    /**
     * Local accounts (the seeded {@code admin}) sign in with a bare username. Mapping
     * it onto the internal domain keeps one identity column instead of two.
     */
    public static final String LOCAL_DOMAIN = "@lifeos.local";

    static String normaliseLogin(String raw) {
        String value = raw.trim().toLowerCase();
        return value.contains("@") ? value : value + LOCAL_DOMAIN;
    }
}
