package com.lifeos.auth.web;

import com.lifeos.auth.dto.AuthDtos.*;
import com.lifeos.auth.service.AuthService;
import com.lifeos.auth.service.GoogleOAuthService;
import com.lifeos.common.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final GoogleOAuthService google;

    public AuthController(AuthService authService, GoogleOAuthService google) {
        this.authService = authService;
        this.google = google;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account with e-mail and password")
    public TokenPair register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        return authService.register(req, http);
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in; returns a token pair, or a 2FA challenge when enrolled")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return ResponseEntity.ok(authService.login(req, http));
    }

    @PostMapping("/verify-2fa")
    @Operation(summary = "Complete a two-factor sign-in with a TOTP or recovery code")
    public TokenPair verifyTwoFactor(@Valid @RequestBody Verify2faRequest req, HttpServletRequest http) {
        return authService.verifyTwoFactor(req, http);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new pair (the old one is burnt)")
    public TokenPair refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest http) {
        return authService.refresh(req.refreshToken(), http);
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the presented refresh token")
    public MessageResponse logout(@RequestBody(required = false) RefreshRequest req,
                                  @AuthenticationPrincipal UserPrincipal principal,
                                  HttpServletRequest http) {
        authService.logout(req == null ? null : req.refreshToken(),
                principal == null ? null : principal.id(), http);
        return MessageResponse.ok("Signed out");
    }

    // ------------------------------------------------------------- Google
    @GetMapping("/oauth2/google/url")
    @Operation(summary = "Build the Google consent URL (state + PKCE handled server-side)")
    public GoogleAuthUrl googleUrl(@RequestParam(required = false) String redirectUri) {
        Map<String, String> result = google.buildAuthorizationUrl(redirectUri);
        return new GoogleAuthUrl(result.get("authorizationUrl"), result.get("state"));
    }

    @PostMapping("/oauth2/google/callback")
    @Operation(summary = "Exchange the Google authorization code for LifeOS tokens")
    public TokenPair googleCallback(@Valid @RequestBody GoogleCallbackRequest req, HttpServletRequest http) {
        return authService.loginWithGoogle(req, http);
    }

    @GetMapping("/providers")
    @Operation(summary = "Which sign-in methods this deployment has configured")
    public Map<String, Boolean> providers() {
        return Map.of("password", true, "google", google.isConfigured());
    }

    // ---------------------------------------------------------- two-factor
    @PostMapping("/2fa/setup")
    @Operation(summary = "Start 2FA enrolment: returns the secret, QR URI and recovery codes")
    public TotpSetup setupTotp(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.beginTotpEnrollment(principal.id());
    }

    @PostMapping("/2fa/confirm")
    @Operation(summary = "Confirm 2FA enrolment with the first generated code")
    public Map<String, Object> confirmTotp(@AuthenticationPrincipal UserPrincipal principal,
                                           @Valid @RequestBody ConfirmTotpRequest req,
                                           HttpServletRequest http) {
        List<String> codes = authService.confirmTotpEnrollment(principal.id(), req.code(), http);
        return Map.of("enabled", true, "recoveryCodes", codes);
    }

    @PostMapping("/2fa/disable")
    @Operation(summary = "Turn 2FA off (requires the current password)")
    public MessageResponse disableTotp(@AuthenticationPrincipal UserPrincipal principal,
                                       @RequestBody Map<String, String> body,
                                       HttpServletRequest http) {
        authService.disableTotp(principal.id(), body.getOrDefault("currentPassword", ""), http);
        return MessageResponse.ok("Two-factor authentication disabled");
    }
}
