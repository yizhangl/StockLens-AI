package com.stocklens.research.ai;

public record AiComparisonPrompt(String systemMessage, String userMessage) {
    public AiComparisonPrompt withRepair(String issues) {
        return new AiComparisonPrompt(systemMessage, userMessage + "\n\nREPAIR REQUIRED: " + issues
                + " Correct only the structured response. Use only supplied source IDs and no new facts.");
    }
}
