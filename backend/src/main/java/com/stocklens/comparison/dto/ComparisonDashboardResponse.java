package com.stocklens.comparison.dto;

import java.util.List;

public record ComparisonDashboardResponse(
        String comparisonId,
        CompanySummaryResponse left,
        CompanySummaryResponse right,
        PricePerformanceResponse pricePerformance,
        List<MetricGroupResponse> metricGroups,
        ComparisonNewsResponse news,
        Object aiBrief,
        ComparisonProvenanceResponse provenance,
        List<ComparisonWarningResponse> warnings) {}
