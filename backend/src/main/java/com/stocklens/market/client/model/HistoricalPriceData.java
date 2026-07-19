package com.stocklens.market.client.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record HistoricalPriceData(
        String ticker,
        LocalDate tradingDate,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal closePrice,
        BigDecimal adjustedClose,
        Long volume,
        String currency,
        String providerName,
        Instant retrievedAt) {

    public HistoricalPriceData withCurrency(String fallbackCurrency) {
        if (currency != null || fallbackCurrency == null) {
            return this;
        }
        return new HistoricalPriceData(
                ticker, tradingDate, openPrice, highPrice, lowPrice, closePrice,
                adjustedClose, volume, fallbackCurrency, providerName, retrievedAt);
    }
}
