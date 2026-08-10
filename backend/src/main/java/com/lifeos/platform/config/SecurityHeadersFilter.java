package com.lifeos.platform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * The hardening headers the gateway used to add on the way out.
 *
 * They are applied before the response is written rather than after, because a
 * servlet response is committed the moment the first byte goes out and a header
 * set afterwards is silently dropped.
 *
 * The order matters more than it looks. Spring Security's chain sits at
 * {@link SecurityProperties#DEFAULT_FILTER_ORDER}, and it answers an
 * unauthenticated request itself without calling anything downstream — so a
 * filter ordered after it never runs on a 401, which is precisely the response
 * an attacker probing the API sees most of. Sitting in front of the chain means
 * every response carries the headers, authenticated or not.
 *
 * The Swagger UI is exempt from the CSP: it is the one HTML page this service
 * serves, and {@code default-src 'none'} would leave it blank.
 */
@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER - 10)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        setIfAbsent(response, "X-Content-Type-Options", "nosniff");
        setIfAbsent(response, "X-Frame-Options", "DENY");
        setIfAbsent(response, "Referrer-Policy", "strict-origin-when-cross-origin");
        setIfAbsent(response, "Permissions-Policy", "geolocation=(), microphone=(), camera=(), payment=()");
        setIfAbsent(response, "Cross-Origin-Resource-Policy", "same-site");
        setIfAbsent(response, "Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        if (!isDocumentation(request)) {
            // Everything else this service returns is JSON, so a restrictive policy
            // costs nothing and closes the door on a reflected-content mistake.
            setIfAbsent(response, "Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
            setIfAbsent(response, "Cache-Control", "no-store");
        }

        chain.doFilter(request, response);
    }

    private static boolean isDocumentation(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
    }

    private static void setIfAbsent(HttpServletResponse response, String name, String value) {
        if (!response.containsHeader(name)) {
            response.setHeader(name, value);
        }
    }
}
