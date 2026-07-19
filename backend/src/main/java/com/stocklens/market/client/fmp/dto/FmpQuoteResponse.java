package com.stocklens.market.client.fmp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FmpQuoteResponse(
        String symbol,
        String name,
        BigDecimal price,
        BigDecimal changePercentage,
        BigDecimal change,
        BigDecimal marketCap,
        String exchange,
        Long timestamp) {}
