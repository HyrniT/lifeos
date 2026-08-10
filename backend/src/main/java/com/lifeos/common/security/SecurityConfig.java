package com.lifeos.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeos.common.api.ApiError;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Stateless resource-server configuration shared by every business service.
 *
 * Each service declares its own anonymous endpoints through
 * {@code lifeos.security.public-paths} so this class never has to know about
 * service-specific routes.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] ALWAYS_PUBLIC = {
            "/actuator/health/**", "/actuator/info", "/actuator/prometheus",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    @Value("${lifeos.security.public-paths:}")
    private String[] publicPaths;

    @Value("${lifeos.cors.allowed-origins:http://localhost:5273,http://localhost:4273}")
    private String[] allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter, ObjectMapper mapper)
            throws Exception {

        String[] anonymous = Arrays.copyOf(ALWAYS_PUBLIC, ALWAYS_PUBLIC.length + publicPaths.length);
        System.arraycopy(publicPaths, 0, anonymous, ALWAYS_PUBLIC.length, publicPaths.length);

        http
            .csrf(AbstractHttpConfigurer::disable)          // stateless bearer-token API
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(reg -> reg
                    .requestMatchers(anonymous).permitAll()
                    .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                    .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((req, res, e) -> {
                        res.setStatus(HttpStatus.UNAUTHORIZED.value());
                        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        mapper.writeValue(res.getOutputStream(), ApiError.of(401, "UNAUTHORIZED",
                                "A valid bearer token is required", req.getRequestURI()));
                    })
                    .accessDeniedHandler((req, res, e) -> {
                        res.setStatus(HttpStatus.FORBIDDEN.value());
                        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        mapper.writeValue(res.getOutputStream(), ApiError.of(403, "FORBIDDEN",
                                "You do not have access to this resource", req.getRequestURI()));
                    }));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of(allowedOrigins));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("Authorization", "X-Total-Count", "X-Request-Id"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
