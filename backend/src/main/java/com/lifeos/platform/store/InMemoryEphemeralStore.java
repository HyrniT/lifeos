package com.lifeos.platform.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A map with expiry times.
 *
 * Reads evict lazily so an expired entry is never observable, and a sweep runs
 * every few minutes so keys nobody asks for again do not accumulate — without it,
 * abandoned OAuth attempts would leak an entry each.
 */
@Component
public class InMemoryEphemeralStore implements EphemeralStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryEphemeralStore.class);

    private record Entry(String value, Instant expiresAt) {
        boolean isLive(Instant now) {
            return expiresAt.isAfter(now);
        }
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public void put(String key, String value, Duration ttl) {
        entries.put(key, new Entry(value, Instant.now().plus(ttl)));
    }

    @Override
    public Optional<String> get(String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.isLive(Instant.now())) {
            entries.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    @Override
    public void remove(String key) {
        entries.remove(key);
    }

    @Override
    public long increment(String key, Duration ttl) {
        Instant now = Instant.now();
        Entry updated = entries.compute(key, (k, current) -> {
            if (current == null || !current.isLive(now)) {
                return new Entry("1", now.plus(ttl));
            }
            long next = parse(current.value()) + 1;
            // The window is set by the first attempt and not extended by later ones,
            // or a steady trickle of failures would keep an account locked forever.
            return new Entry(Long.toString(next), current.expiresAt());
        });
        return parse(updated.value());
    }

    @Override
    public void expire(String key, Duration ttl) {
        entries.computeIfPresent(key, (k, current) -> new Entry(current.value(), Instant.now().plus(ttl)));
    }

    @Scheduled(fixedDelay = 300_000L)
    void sweep() {
        Instant now = Instant.now();
        int before = entries.size();
        entries.entrySet().removeIf(e -> !e.getValue().isLive(now));
        int removed = before - entries.size();
        if (removed > 0) {
            log.debug("Swept {} expired entr{}", removed, removed == 1 ? "y" : "ies");
        }
    }

    private static long parse(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
