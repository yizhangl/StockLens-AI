package com.stocklens.research.ai;

import java.util.List;

/** Untrusted model output; it is never returned or persisted before validation. */
public record AiComparisonResult(
        String overallSummary,
        AiAdvantages advantages,
        List<AiRiskResult> keyRisks,
        List<String> sourceIds) {}
