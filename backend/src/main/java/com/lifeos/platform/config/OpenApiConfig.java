package com.lifeos.platform.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One API document instead of six.
 *
 * The bounded contexts are still visible: each is a group in the Swagger UI's
 * dropdown, selected by URL prefix, which is how they were separated at the gateway
 * anyway.
 */
@Configuration
public class OpenApiConfig {

    private static final String SCHEME = "bearer-jwt";

    @Bean
    public OpenAPI lifeOsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("LifeOS API")
                        .version("2.0.0")
                        .description("""
                                Habits, money, planning, analytics and notifications in one service.

                                Everything except the endpoints under `/api/auth` needs a bearer token;
                                get one from `POST /api/auth/login`.

                                The habit context is event sourced: every mutation appends to
                                `habit.event_store`, and the tables the read endpoints serve are
                                projections that can be rebuilt from it at any time via
                                `POST /api/habits/projections/rebuild`."""))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME))
                .components(new Components().addSecuritySchemes(SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    @Bean
    public GroupedOpenApi authApi() {
        return group("1-auth", "/api/auth/**", "/api/users/**", "/api/admin/**");
    }

    @Bean
    public GroupedOpenApi habitApi() {
        return group("2-habits", "/api/habits/**", "/api/gamification/**");
    }

    @Bean
    public GroupedOpenApi moneyApi() {
        return group("3-money", "/api/expenses/**", "/api/accounts/**",
                "/api/budgets/**", "/api/categories/**");
    }

    @Bean
    public GroupedOpenApi planningApi() {
        return group("4-planning", "/api/tasks/**", "/api/goals/**",
                "/api/projects/**", "/api/focus/**", "/api/journal/**");
    }

    @Bean
    public GroupedOpenApi analyticsApi() {
        return group("5-analytics", "/api/analytics/**", "/api/insights/**");
    }

    @Bean
    public GroupedOpenApi notificationApi() {
        return group("6-notifications", "/api/notifications/**");
    }

    private static GroupedOpenApi group(String name, String... paths) {
        return GroupedOpenApi.builder().group(name).pathsToMatch(paths).build();
    }
}
