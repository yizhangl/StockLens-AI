package com.stocklens.market.client.fmp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FmpFinancialGrowthResponse(
        String symbol,
        LocalDate date,
        String fiscalYear,
        String period,
        String reportedCurrency,
        BigDecimal revenueGrowth,
        BigDecimal netIncomeGrowth) {}
