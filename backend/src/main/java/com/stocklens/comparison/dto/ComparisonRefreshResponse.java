package com.stocklens.comparison.dto;

import java.util.List;

public record ComparisonRefreshResponse(List<String> tickers, boolean regenerateBrief, List<String> warnings) {}
