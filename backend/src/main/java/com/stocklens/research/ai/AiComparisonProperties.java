package com.stocklens.research.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stocklens.research.ai")
public record AiComparisonProperties(String apiKey, String model, double temperature, int maxTokens) {
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && model != null && !model.isBlank();
    }
}
