package com.stocklens.comparison.dto;

import com.stocklens.comparison.model.ComparisonMode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PricePerformanceResponse(
        String period,
        ComparisonMode mode,
        LocalDate startDate,
        LocalDate endDate,
        int pointCount,
        BigDecimal leftReturnPercent,
        BigDecimal rightReturnPercent,
        String leftCurrency,
        String rightCurrency,
        List<PricePerformancePoint> series) {}
