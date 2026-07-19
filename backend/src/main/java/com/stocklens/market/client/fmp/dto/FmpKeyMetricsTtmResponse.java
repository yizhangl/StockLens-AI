package com.stocklens.market.client.fmp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FmpKeyMetricsTtmResponse(String symbol, BigDecimal returnOnEquityTTM) {}
