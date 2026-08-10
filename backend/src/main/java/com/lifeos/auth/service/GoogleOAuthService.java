package com.lifeos.auth.service;

import com.lifeos.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import com.lifeos.platform.store.EphemeralStore;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Google sign-in using the authorization-code flow with PKCE, driven from the
 * backend.
 *
 * The SPA never sees the client secret and never handles an id_token directly:
 * it asks for a consent URL, Google redirects back with a code, the SPA posts the
 * code here, and this service exchanges it and returns LifeOS tokens. That keeps
 * exactly one token format in the browser.
 */
@Service
public class GoogleOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthService.class);

    private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_ENDPOINT = "https://openidconnect.googleapis.com/v1/userinfo";

    private static final String STATE_KEY = "lifeos:oauth:state:";
    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    private final EphemeralStore ephemeral;
    private final RestClient restClient;
    private final SecureRandom random = new SecureRandom();

    private final String clientId;
    private final String clientSecret;
    private final String defaultRedirectUri;

    public GoogleOAuthService(
            EphemeralStore ephemeral,
            RestClient.Builder restClientBuilder,
            @Value("${lifeos.oauth.google.client-id:}") String clientId,
            @Value("${lifeos.oauth.google.client-secret:}") String clientSecret,
            @Value("${lifeos.oauth.google.redirect-uri:http://localhost:5273/auth/google/callback}")
            String defaultRedirectUri) {
        this.ephemeral = ephemeral;
        this.restClient = restClientBuilder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.defaultRedirectUri = defaultRedirectUri;
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    public record GoogleProfile(String subject, String email, boolean emailVerified, String name, String picture) {
    }

    /**
     * Builds the consent URL and stashes the CSRF state plus the PKCE verifier
     * for the length of the round trip, keyed by state.
     */
    public Map<String, String> buildAuthorizationUrl(String redirectUri) {
        requireConfigured();

        String state = randomUrlSafe(32);
        String codeVerifier = randomUrlSafe(64);
        String effectiveRedirect = (redirectUri == null || redirectUri.isBlank()) ? defaultRedirectUri : redirectUri;

        ephemeral.put(STATE_KEY + state, codeVerifier + "|" + effectiveRedirect, STATE_TTL);

        String url = UriComponentsBuilder.fromUriString(AUTH_ENDPOINT)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", URLEncoder.encode(effectiveRedirect, StandardCharsets.UTF_8))
                .queryParam("response_type", "code")
                .queryParam("scope", URLEncoder.encode("openid email profile", StandardCharsets.UTF_8))
                .queryParam("state", state)
                .queryParam("code_challenge", s256(codeVerifier))
                .queryParam("code_challenge_method", "S256")
                .queryParam("access_type", "online")
                .queryParam("prompt", "select_account")
                .build(true)
                .toUriString();

        return Map.of("authorizationUrl", url, "state", state);
    }

    /** Exchanges the authorization code and returns the verified Google profile. */
    @SuppressWarnings("unchecked")
    public GoogleProfile exchangeCode(String code, String state, String redirectUriOverride) {
        requireConfigured();

        String stored = ephemeral.get(STATE_KEY + state).orElse(null);
        if (stored == null) {
            throw ApiException.badRequest("OAuth state is invalid or has expired. Please start the sign-in again.");
        }
        // One-shot: a replayed state must not work.
        ephemeral.remove(STATE_KEY + state);

        String[] parts = stored.split("\\|", 2);
        String codeVerifier = parts[0];
        String redirectUri = redirectUriOverride != null && !redirectUriOverride.isBlank()
                ? redirectUriOverride
                : (parts.length > 1 ? parts[1] : defaultRedirectUri);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");
        form.add("code_verifier", codeVerifier);

        Map<String, Object> tokenResponse;
        try {
            tokenResponse = restClient.post()
                    .uri(TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception ex) {
            log.error("Google token exchange failed", ex);
            throw ApiException.badRequest("Google rejected the authorization code: " + ex.getMessage());
        }

        if (tokenResponse == null || tokenResponse.get("access_token") == null) {
            throw ApiException.badRequest("Google did not return an access token");
        }
        String accessToken = String.valueOf(tokenResponse.get("access_token"));

        Map<String, Object> profile;
        try {
            profile = restClient.get()
                    .uri(USERINFO_ENDPOINT)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception ex) {
            log.error("Google userinfo lookup failed", ex);
            throw ApiException.badRequest("Could not read your Google profile");
        }

        if (profile == null || profile.get("sub") == null) {
            throw ApiException.badRequest("Google profile response was empty");
        }

        return new GoogleProfile(
                String.valueOf(profile.get("sub")),
                (String) profile.get("email"),
                Boolean.TRUE.equals(profile.get("email_verified")),
                (String) profile.getOrDefault("name", profile.get("email")),
                (String) profile.get("picture"));
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "OAUTH_NOT_CONFIGURED",
                    "Google sign-in is not configured on this deployment. "
                    + "Set lifeos.oauth.google.client-id and client-secret.");
        }
    }

    private String randomUrlSafe(int bytes) {
        byte[] buf = new byte[bytes];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static String s256(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(digest.digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
