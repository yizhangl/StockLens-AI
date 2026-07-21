package com.stocklens.research.dto;

import java.time.Instant;
import java.util.List;

public record ComparisonResearchResponse(
        Long id,
        String leftTicker,
        String rightTicker,
        String overallSummary,
        AdvantagesResponse advantages,
        List<RiskResponse> keyRisks,
        List<GroundedSourceResponse> sources,
        String modelName,
        String promptVersion,
        Instant generatedAt,
        Instant dataCutoffAt,
        boolean cached) {}
