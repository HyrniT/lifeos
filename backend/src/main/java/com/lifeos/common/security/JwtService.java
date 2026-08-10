package com.lifeos.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Issues and verifies the HS512 tokens shared by every service.
 *
 * The gateway verifies once at the edge and forwards the resolved identity as
 * X-User-* headers, but each service re-verifies the bearer token as well so a
 * service is never reliant on being called through the gateway.
 */
@Component
public class JwtService {

    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_NAME = "name";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;

    public JwtService(
            @Value("${lifeos.jwt.secret}") String secret,
            @Value("${lifeos.jwt.access-ttl-seconds:3600}") long accessTtlSeconds,
            @Value("${lifeos.jwt.refresh-ttl-seconds:2592000}") long refreshTtlSeconds) {

        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 64) {
            throw new IllegalStateException(
                    "lifeos.jwt.secret must be at least 64 bytes for HS512 (got " + raw.length + ")");
        }
        this.key = Keys.hmacShaKeyFor(raw);
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    public String issueAccessToken(UUID userId, String email, String name, List<String> roles) {
        return build(userId, Map.of(
                CLAIM_EMAIL, email,
                CLAIM_NAME, name == null ? "" : name,
                CLAIM_ROLES, roles,
                CLAIM_TYPE, TYPE_ACCESS
        ), accessTtlSeconds);
    }

    public String issueRefreshToken(UUID userId) {
        return build(userId, Map.of(CLAIM_TYPE, TYPE_REFRESH), refreshTtlSeconds);
    }

    private String build(UUID userId, Map<String, Object> claims, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claims(claims)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .issuer("lifeos")
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    /** Returns the parsed claims, or throws {@link io.jsonwebtoken.JwtException} when invalid. */
    public Claims parse(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(key)
                .requireIssuer("lifeos")
                .build()
                .parseSignedClaims(token);
        return jws.getPayload();
    }

    @SuppressWarnings("unchecked")
    public UserPrincipal toPrincipal(Claims claims) {
        Object rolesClaim = claims.get(CLAIM_ROLES);
        List<String> roles = rolesClaim instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of("USER");
        return new UserPrincipal(
                UUID.fromString(claims.getSubject()),
                claims.get(CLAIM_EMAIL, String.class),
                claims.get(CLAIM_NAME, String.class),
                roles);
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }

    public long getRefreshTtlSeconds() {
        return refreshTtlSeconds;
    }
}
