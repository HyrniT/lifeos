package com.lifeos.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Feeds the admin console's system panel.
 *
 * The microservice edition asked Eureka which instances were up. There is no
 * registry now and nothing to be up or down independently, so this reports the
 * modules that make up the single process and the one dependency it actually has.
 * The response shape is unchanged, because the console renders it and a rewrite of
 * the console is not what this is about.
 *
 * Every probe is wrapped: an unreachable dependency must render as a red tile,
 * never as a 500 that takes the whole page down.
 */
@Service
public class SystemStatusService {

    private static final Logger log = LoggerFactory.getLogger(SystemStatusService.class);

    /** The bounded contexts, and the URL prefixes each one answers on. */
    private static final Map<String, String> MODULES = new LinkedHashMap<>(Map.of(
            "auth", "/api/auth, /api/users, /api/admin",
            "habit", "/api/habits, /api/gamification",
            "expense", "/api/expenses, /api/accounts, /api/budgets, /api/categories",
            "planning", "/api/tasks, /api/goals, /api/projects, /api/focus, /api/journal",
            "analytics", "/api/analytics, /api/insights",
            "notification", "/api/notifications"));

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;
    private final String applicationName;

    public SystemStatusService(JdbcTemplate jdbcTemplate, Environment environment,
                               @Value("${spring.application.name:lifeos}") String applicationName) {
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
        this.applicationName = applicationName;
    }

    public record ServiceInstanceView(
            String serviceId,
            String instanceId,
            String host,
            int port,
            String uri,
            boolean secure,
            Map<String, String> metadata
    ) {
    }

    /**
     * One entry per module, all reporting the same host and port — because they are
     * the same process. The console's list stays meaningful: it shows what is
     * deployed and where each part answers.
     */
    public List<ServiceInstanceView> registeredInstances() {
        String host = environment.getProperty("HOSTNAME", "localhost");
        int port = Integer.parseInt(environment.getProperty("server.port", "9080"));

        // Not getProperty(key, default): the property is declared as ${PUBLIC_URL:}
        // and resolves to an empty string when the variable is unset, so the default
        // would never be reached and the console would render a blank link.
        String configured = environment.getProperty("lifeos.public-url", "");
        String publicUrl = configured.isBlank() ? "http://%s:%d".formatted(host, port) : configured;

        return MODULES.entrySet().stream()
                .map(entry -> new ServiceInstanceView(
                        entry.getKey(),
                        applicationName + ":" + entry.getKey(),
                        host,
                        port,
                        publicUrl,
                        publicUrl.startsWith("https"),
                        Map.of("paths", entry.getValue(), "deployment", "single-process")))
                .toList();
    }

    public Map<String, Object> infrastructureHealth() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkedAt", Instant.now().toString());
        result.put("postgres", probe("postgres", () -> {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            String database = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
            return "SELECT 1 -> " + one + " on " + database;
        }));
        result.put("application", probe("application", () -> {
            Runtime runtime = Runtime.getRuntime();
            long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
            long maxMb = runtime.maxMemory() / (1024 * 1024);
            long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
            return "%d/%d MB heap, up %d min, %d module(s)"
                    .formatted(usedMb, maxMb, uptimeSeconds / 60, MODULES.size());
        }));
        return result;
    }

    private Map<String, Object> probe(String name, ThrowingSupplier probe) {
        long started = System.nanoTime();
        try {
            String detail = probe.get();
            return Map.of(
                    "status", "UP",
                    "latencyMs", Duration.ofNanos(System.nanoTime() - started).toMillis(),
                    "detail", detail);
        } catch (Exception ex) {
            log.debug("Health probe {} failed", name, ex);
            return Map.of(
                    "status", "DOWN",
                    "latencyMs", Duration.ofNanos(System.nanoTime() - started).toMillis(),
                    "detail", String.valueOf(ex.getMessage()));
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        String get() throws Exception;
    }
}
