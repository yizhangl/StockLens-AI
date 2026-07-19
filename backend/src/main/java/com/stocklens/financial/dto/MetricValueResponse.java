package com.stocklens.financial.dto;

import com.stocklens.financial.metric.ComparisonStrategy;
import com.stocklens.financial.metric.MetricCategory;
import com.stocklens.financial.metric.MetricCode;
import com.stocklens.financial.metric.MetricUnit;
import java.math.BigDecimal;

public record MetricValueResponse(
        MetricCode code,
        String displayName,
        MetricCategory category,
        MetricUnit unit,
        ComparisonStrategy comparisonStrategy,
        BigDecimal value,
        String description) {}
