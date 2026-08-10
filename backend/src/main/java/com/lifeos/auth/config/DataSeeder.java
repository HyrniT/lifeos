package com.lifeos.auth.config;

import com.lifeos.auth.domain.User;
import com.lifeos.auth.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates the bootstrap administrator on an empty database.
 *
 * The default credentials are admin/admin because that is what the operator asked
 * for; the account is flagged and logged loudly so nobody ships it unchanged.
 */
@Configuration
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    public ApplicationRunner seedAdmin(UserRepository users,
                                       PasswordEncoder encoder,
                                       @Value("${lifeos.admin.username:admin}") String username,
                                       @Value("${lifeos.admin.password:admin}") String password,
                                       @Value("${lifeos.admin.enabled:true}") boolean enabled) {
        return args -> {
            if (!enabled) {
                return;
            }
            String email = username.toLowerCase() + com.lifeos.auth.service.AuthService.LOCAL_DOMAIN;
            if (users.existsByEmailIgnoreCase(email)) {
                log.info("Bootstrap administrator '{}' already present.", username);
                return;
            }

            users.save(User.builder()
                    .email(email)
                    .passwordHash(encoder.encode(password))
                    .displayName("Administrator")
                    .roles(new ArrayList<>(List.of("ADMIN", "USER")))
                    .enabled(true)
                    .emailVerified(true)
                    .timezone("UTC")
                    .baseCurrency("USD")
                    .passwordChangedAt(Instant.now())
                    .build());

            log.warn("""

                    ============================================================
                     Bootstrap administrator created
                       username : {}
                       password : {}
                     CHANGE THIS BEFORE EXPOSING THE DEPLOYMENT TO THE INTERNET.
                     Override with LIFEOS_ADMIN_USERNAME / LIFEOS_ADMIN_PASSWORD,
                     or set lifeos.admin.enabled=false to skip seeding entirely.
                    ============================================================
                    """, username, password);
        };
    }
}
