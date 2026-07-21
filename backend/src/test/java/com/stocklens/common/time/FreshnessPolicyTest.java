package com.stocklens.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class FreshnessPolicyTest {
    private static final Instant NOW = Instant.parse("2026-07-20T12:00:00Z");
    private final FreshnessPolicy policy = new FreshnessPolicy(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void usesExclusiveExpiryBoundaryAndAcceptsFutureTimestamps() {
        Duration ttl = Duration.ofHours(6);
        assertThat(policy.isFresh(null, ttl)).isFalse();
        assertThat(policy.isFresh(NOW.minus(ttl).plusNanos(1), ttl)).isTrue();
        assertThat(policy.isFresh(NOW.minus(ttl), ttl)).isFalse();
        assertThat(policy.isFresh(NOW.minus(ttl).minusNanos(1), ttl)).isFalse();
        assertThat(policy.isFresh(NOW.plusSeconds(30), ttl)).isTrue();
    }
}
