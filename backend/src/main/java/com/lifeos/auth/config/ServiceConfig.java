package com.lifeos.auth.config;

import com.lifeos.auth.service.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class ServiceConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        return RestClient.builder().requestFactory(factory);
    }

    /** Housekeeping so the refresh-token table does not grow without bound. */
    @Component
    static class TokenMaintenance {

        private static final Logger log = LoggerFactory.getLogger(TokenMaintenance.class);
        private final TokenService tokenService;

        TokenMaintenance(TokenService tokenService) {
            this.tokenService = tokenService;
        }

        @Scheduled(cron = "0 15 3 * * *")
        void purgeExpiredTokens() {
            int removed = tokenService.purgeExpired();
            if (removed > 0) {
                log.info("Purged {} expired refresh token(s)", removed);
            }
        }
    }
}
