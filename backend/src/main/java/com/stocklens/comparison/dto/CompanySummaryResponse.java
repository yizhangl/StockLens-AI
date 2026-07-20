package com.stocklens.comparison.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CompanySummaryResponse(
        String ticker,
        String companyName,
        String exchange,
        String sector,
        String industry,
        String country,
        String website,
        String logoUrl,
        String description,
        BigDecimal price,
        BigDecimal priceChange,
        BigDecimal priceChangePercent,
        BigDecimal marketCap,
        String currency,
        Instant quoteTimestamp,
        BigDecimal peTtm,
        BigDecimal revenueTtm,
        Instant companyUpdatedAt,
        Instant marketRetrievedAt,
        Instant metricsRetrievedAt) {}
