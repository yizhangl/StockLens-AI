package com.stocklens.market.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketSnapshotResponse(
        BigDecimal price,
        BigDecimal priceChange,
        BigDecimal priceChangePercent,
        BigDecimal marketCap,
        String currency,
        Instant quoteTimestamp,
        Instant retrievedAt,
        String providerName) {}
