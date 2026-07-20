package com.stocklens.comparison.dto;

import java.time.Instant;
import java.time.LocalDate;

public record ComparisonProvenanceResponse(
        String financialProvider,
        String newsProvider,
        Instant leftCompanyUpdatedAt,
        Instant rightCompanyUpdatedAt,
        Instant leftMarketRetrievedAt,
        Instant rightMarketRetrievedAt,
        Instant leftMetricsRetrievedAt,
        Instant rightMetricsRetrievedAt,
        Instant leftHistoryRetrievedAt,
        Instant rightHistoryRetrievedAt,
        Instant leftNewsRetrievedAt,
        Instant rightNewsRetrievedAt,
        LocalDate historyStartDate,
        LocalDate historyEndDate,
        Instant lastUpdatedAt,
        boolean cached) {}
