package com.stocklens.market.client.model;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketSnapshotData(
        String ticker,
        BigDecimal price,
        BigDecimal priceChange,
        BigDecimal priceChangePercent,
        BigDecimal marketCap,
        String currency,
        Instant quoteTimestamp,
        Instant retrievedAt,
        String providerName) {

    public MarketSnapshotData withCurrency(String fallbackCurrency) {
        if (currency != null || fallbackCurrency == null) {
            return this;
        }
        return new MarketSnapshotData(
                ticker,
                price,
                priceChange,
                priceChangePercent,
                marketCap,
                fallbackCurrency,
                quoteTimestamp,
                retrievedAt,
                providerName);
    }
}
