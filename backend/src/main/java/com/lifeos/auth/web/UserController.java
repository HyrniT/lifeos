package com.lifeos.auth.web;

import com.lifeos.auth.dto.AuthDtos.*;
import com.lifeos.auth.service.AuthService;
import com.lifeos.common.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Current user")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in user's profile")
    public UserView me(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.me(principal.id());
    }

    @PatchMapping("/me")
    @Operation(summary = "Update display name, avatar, locale, timezone or base currency")
    public UserView updateMe(@AuthenticationPrincipal UserPrincipal principal,
                             @Valid @RequestBody UpdateProfileRequest req) {
        return authService.updateProfile(principal.id(), req);
    }

    @PostMapping("/me/password")
    @Operation(summary = "Change the password; all other sessions are signed out")
    public MessageResponse changePassword(@AuthenticationPrincipal UserPrincipal principal,
                                          @Valid @RequestBody ChangePasswordRequest req,
                                          HttpServletRequest http) {
        authService.changePassword(principal.id(), req, http);
        return MessageResponse.ok("Password updated. Other devices have been signed out.");
    }

    @GetMapping("/me/sessions")
    @Operation(summary = "List active refresh-token sessions for this account")
    public List<SessionView> sessions(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.sessions(principal.id());
    }

    @DeleteMapping("/me/sessions")
    @Operation(summary = "Sign out everywhere")
    public MessageResponse revokeSessions(@AuthenticationPrincipal UserPrincipal principal) {
        int count = authService.revokeAllSessions(principal.id());
        return MessageResponse.ok("Revoked " + count + " session(s)");
    }
}
