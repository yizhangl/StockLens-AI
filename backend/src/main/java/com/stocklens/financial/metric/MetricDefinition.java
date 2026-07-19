package com.stocklens.financial.metric;

public record MetricDefinition(
        MetricCode code,
        String displayName,
        MetricCategory category,
        MetricUnit unit,
        ComparisonStrategy comparisonStrategy,
        String description) {}
