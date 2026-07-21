package com.stocklens.research.ai;

import java.util.List;

public record AiRiskResult(String ticker, String text, List<String> sourceIds) {}
