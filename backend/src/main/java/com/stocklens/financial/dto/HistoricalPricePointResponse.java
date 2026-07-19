package com.stocklens.financial.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HistoricalPricePointResponse(
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal adjustedClose,
        Long volume) {}
