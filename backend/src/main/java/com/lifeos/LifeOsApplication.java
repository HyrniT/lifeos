package com.lifeos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * LifeOS, as one process.
 *
 * The same six bounded contexts the microservice edition split across six
 * deployments — auth, habit, expense, planning, analytics, notification — live here
 * as packages instead. Nothing about the domain changed; what changed is that the
 * boundaries are enforced by package structure and an in-process event bus rather
 * than by a network, which removes the broker, the registry, the gateway and the
 * cache from the list of things that have to be running for the app to work.
 *
 * @see com.lifeos.platform.bus.InProcessEventPublisher for what replaced Kafka
 * @see com.lifeos.platform.config.FlywayConfig for how the six schemas are migrated
 */
@SpringBootApplication
@EnableScheduling
// The auth entities carry @CreatedDate / @LastModifiedDate. Without this the
// columns are simply left null, and the failure surfaces as a not-null violation
// on the very first registration rather than as anything that names auditing.
@EnableJpaAuditing
public class LifeOsApplication {

    public static void main(String[] args) {
        SpringApplication.run(LifeOsApplication.class, args);
    }
}
