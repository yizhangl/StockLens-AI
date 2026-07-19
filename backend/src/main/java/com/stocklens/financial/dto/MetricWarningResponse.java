package com.stocklens.financial.dto;

import com.stocklens.financial.metric.MetricCode;

public record MetricWarningResponse(MetricCode metricCode, String message) {}
