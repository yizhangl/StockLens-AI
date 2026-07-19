package com.stocklens.market.client.fmp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FmpCompanyProfileResponse(
        String symbol,
        String companyName,
        String currency,
        String exchangeFullName,
        String exchange,
        String industry,
        String website,
        String description,
        String sector,
        String country,
        String image) {}
