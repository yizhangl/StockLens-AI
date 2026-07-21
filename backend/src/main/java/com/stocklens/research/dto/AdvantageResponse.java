package com.stocklens.research.dto;

import java.util.List;

public record AdvantageResponse(String winner, String explanation, List<String> sourceIds) {}
