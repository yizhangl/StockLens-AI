package com.stocklens.common.time;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FreshnessPolicy {
    private static final Logger log = LoggerFactory.getLogger(FreshnessPolicy.class);
    private final Clock clock;
    public FreshnessPolicy(Clock clock) { this.clock = clock; }
    public boolean isFresh(Instant timestamp, Duration ttl) {
        if (timestamp == null) return false;
        Instant now = clock.instant();
        if (timestamp.isAfter(now.plus(Duration.ofMinutes(5)))) log.warn("Persisted source timestamp is materially in the future");
        return timestamp.isAfter(now.minus(ttl));
    }
}
