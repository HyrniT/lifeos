package com.lifeos.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** The authenticated caller, resolved from the bearer token on every request. */
public record UserPrincipal(UUID id, String email, String name, List<String> roles) implements Principal {

    @Override
    public String getName() {
        return id.toString();
    }

    public Collection<? extends GrantedAuthority> authorities() {
        return roles.stream()
                .map(r -> new SimpleGrantedAuthority(r.startsWith("ROLE_") ? r : "ROLE_" + r))
                .map(GrantedAuthority.class::cast)
                .toList();
    }
}
