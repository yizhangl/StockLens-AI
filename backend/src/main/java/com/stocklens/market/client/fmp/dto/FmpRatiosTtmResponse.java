package com.stocklens.market.client.fmp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FmpRatiosTtmResponse(
        String symbol,
        BigDecimal priceToEarningsRatioTTM,
        BigDecimal priceToEarningsGrowthRatioTTM,
        BigDecimal priceToSalesRatioTTM,
        BigDecimal grossProfitMarginTTM,
        BigDecimal netProfitMarginTTM,
        BigDecimal debtToEquityRatioTTM,
        BigDecimal currentRatioTTM) {}
