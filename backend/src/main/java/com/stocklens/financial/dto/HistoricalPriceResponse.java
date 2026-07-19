package com.stocklens.financial.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record HistoricalPriceResponse(
        String ticker,
        String period,
        LocalDate startDate,
        LocalDate endDate,
        String currency,
        BigDecimal returnPercent,
        String providerName,
        Instant retrievedAt,
        List<HistoricalPricePointResponse> prices) {}
