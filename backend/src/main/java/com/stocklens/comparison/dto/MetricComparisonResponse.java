package com.stocklens.comparison.dto;

import com.stocklens.comparison.model.ComparisonOutcome;
import com.stocklens.financial.metric.ComparisonStrategy;
import com.stocklens.financial.metric.MetricCategory;
import com.stocklens.financial.metric.MetricCode;
import com.stocklens.financial.metric.MetricUnit;
import java.math.BigDecimal;

public record MetricComparisonResponse(
        MetricCode code,
        String displayName,
        MetricCategory category,
        MetricUnit unit,
        BigDecimal leftValue,
        BigDecimal rightValue,
        ComparisonStrategy comparisonStrategy,
        ComparisonOutcome outcome,
        String explanation) {}
