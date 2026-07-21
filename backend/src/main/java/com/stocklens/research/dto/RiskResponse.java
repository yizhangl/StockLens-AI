package com.stocklens.research.dto;

import java.util.List;

public record RiskResponse(String ticker, String text, List<String> sourceIds) {}
