package com.lifeos.auth.web;

import com.lifeos.auth.dto.AuthDtos.*;
import com.lifeos.auth.service.AdminService;
import com.lifeos.auth.service.SystemStatusService;
import com.lifeos.common.api.PageResponse;
import com.lifeos.common.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the admin console talks to.
 *
 * {@code @PreAuthorize} is the lock. The edge filter that used to reject
 * non-admins before the request reached a service is gone with the gateway, which
 * makes this the only one — so it is on the class, not on individual methods.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration")
public class AdminController {

    private final AdminService adminService;
    private final SystemStatusService systemStatus;

    public AdminController(AdminService adminService, SystemStatusService systemStatus) {
        this.adminService = adminService;
        this.systemStatus = systemStatus;
    }

    // ------------------------------------------------------------- overview
    @GetMapping("/overview")
    @Operation(summary = "Headline platform metrics for the admin dashboard")
    public AdminOverview overview() {
        return adminService.overview();
    }

    @GetMapping("/system/services")
    @Operation(summary = "The modules that make up this deployment and where each answers")
    public List<SystemStatusService.ServiceInstanceView> services() {
        return systemStatus.registeredInstances();
    }

    @GetMapping("/system/health")
    @Operation(summary = "Reachability of Postgres, plus this process' own vitals")
    public Map<String, Object> systemHealth() {
        return systemStatus.infrastructureHealth();
    }

    // ---------------------------------------------------------------- users
    @GetMapping("/users")
    @Operation(summary = "Search and page through accounts")
    public PageResponse<UserView> listUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.fromString(direction), sort));
        return adminService.listUsers(q, enabled, pageable);
    }

    @GetMapping("/users/{id}")
    public UserView getUser(@PathVariable UUID id) {
        return adminService.getUser(id);
    }

    @PatchMapping("/users/{id}")
    @Operation(summary = "Enable/disable an account or change its roles")
    public UserView updateUser(@PathVariable UUID id,
                               @Valid @RequestBody AdminUpdateUserRequest req,
                               @AuthenticationPrincipal UserPrincipal principal,
                               HttpServletRequest http) {
        return adminService.updateUser(id, req, principal.id(), http);
    }

    @PostMapping("/users/{id}/sign-out")
    @Operation(summary = "Revoke every session belonging to this account")
    public MessageResponse forceSignOut(@PathVariable UUID id) {
        int count = adminService.forceSignOut(id);
        return MessageResponse.ok("Revoked " + count + " session(s)");
    }

    // ---------------------------------------------------------------- audit
    @GetMapping("/audit")
    @Operation(summary = "Security audit trail, newest first")
    public PageResponse<AuditView> audit(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) Instant since,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 200));
        return adminService.auditTrail(userId, action, outcome, since, pageable);
    }
}
