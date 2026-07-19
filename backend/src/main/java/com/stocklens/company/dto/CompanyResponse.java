package com.stocklens.company.dto;

import java.time.Instant;

public record CompanyResponse(
        String ticker,
        String name,
        String exchange,
        String sector,
        String industry,
        String country,
        String websiteUrl,
        String description,
        String logoUrl,
        Instant profileUpdatedAt) {}
