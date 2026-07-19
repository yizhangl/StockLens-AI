package com.stocklens.market.client.model;

import java.time.Instant;

public record CompanyProfileData(
        String ticker,
        String name,
        String exchange,
        String sector,
        String industry,
        String country,
        String websiteUrl,
        String description,
        String logoUrl,
        String providerSymbol,
        String currency,
        Instant retrievedAt) {}
