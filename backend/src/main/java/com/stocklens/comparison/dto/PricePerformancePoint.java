package com.stocklens.comparison.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PricePerformancePoint(
        LocalDate date,
        BigDecimal leftValue,
        BigDecimal rightValue) {}
