package com.stocklens.market.client.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record FinancialMetricsData(
        String ticker,
        BigDecimal peTtm,
        BigDecimal forwardPe,
        BigDecimal pegRatio,
        BigDecimal priceToSales,
        BigDecimal revenueTtm,
        BigDecimal grossMargin,
        BigDecimal netMargin,
        BigDecimal returnOnEquity,
        BigDecimal revenueGrowth,
        BigDecimal earningsGrowth,
        BigDecimal debtToEquity,
        BigDecimal currentRatio,
        BigDecimal beta,
        String currency,
        LocalDate reportedAt,
        Instant retrievedAt,
        String providerName) {

    public FinancialMetricsData withCurrency(String fallbackCurrency) {
        if (currency != null || fallbackCurrency == null) {
            return this;
        }
        return new FinancialMetricsData(
                ticker, peTtm, forwardPe, pegRatio, priceToSales, revenueTtm,
                grossMargin, netMargin, returnOnEquity, revenueGrowth,
                earningsGrowth, debtToEquity, currentRatio, beta,
                fallbackCurrency, reportedAt, retrievedAt, providerName);
    }
}
