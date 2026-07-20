package com.stocklens.comparison.dto;

import com.stocklens.financial.metric.MetricCategory;
import java.util.List;

public record MetricGroupResponse(
        MetricCategory category,
        List<MetricComparisonResponse> metrics) {}
