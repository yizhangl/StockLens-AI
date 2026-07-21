package com.stocklens.comparison.dto;

import java.util.List;

public record ComparisonRefreshRequest(List<String> tickers, Boolean regenerateBrief) {}
