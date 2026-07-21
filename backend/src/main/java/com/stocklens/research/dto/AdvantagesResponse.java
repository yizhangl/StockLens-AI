package com.stocklens.research.dto;

public record AdvantagesResponse(
        AdvantageResponse valuation,
        AdvantageResponse profitability,
        AdvantageResponse growth,
        AdvantageResponse financialHealth) {}
