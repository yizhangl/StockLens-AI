package com.stocklens.common.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("stocklens.cache")
public record StockLensCacheProperties(Duration companyTtl, Duration marketTtl, Duration metricsTtl,
        Duration historyTtl, Duration newsTtl, Duration comparisonTtl, Duration briefTtl) {
    public StockLensCacheProperties {
        companyTtl = valueOrDefault(companyTtl, Duration.ofHours(24));
        marketTtl = valueOrDefault(marketTtl, Duration.ofMinutes(15));
        metricsTtl = valueOrDefault(metricsTtl, Duration.ofHours(6));
        historyTtl = valueOrDefault(historyTtl, Duration.ofHours(6));
        newsTtl = valueOrDefault(newsTtl, Duration.ofMinutes(30));
        comparisonTtl = valueOrDefault(comparisonTtl, Duration.ofMinutes(15));
        briefTtl = valueOrDefault(briefTtl, Duration.ofHours(1));
    }
    private static Duration valueOrDefault(Duration value, Duration fallback) { return value == null ? fallback : value; }
}
