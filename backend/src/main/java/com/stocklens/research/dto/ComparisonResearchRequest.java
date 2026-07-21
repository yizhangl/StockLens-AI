package com.stocklens.research.dto;

public record ComparisonResearchRequest(String leftTicker, String rightTicker, Boolean forceRefresh) {
    public ComparisonResearchRequest(String leftTicker, String rightTicker) { this(leftTicker, rightTicker, false); }
}
