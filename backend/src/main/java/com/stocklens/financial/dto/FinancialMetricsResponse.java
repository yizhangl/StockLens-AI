package com.stocklens.financial.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record FinancialMetricsResponse(
        String ticker,
        String currency,
        LocalDate reportedAt,
        Instant retrievedAt,
        String providerName,
        List<MetricValueResponse> metrics,
        List<MetricWarningResponse> warnings) {}
