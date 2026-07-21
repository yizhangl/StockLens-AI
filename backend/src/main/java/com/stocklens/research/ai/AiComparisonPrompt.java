package com.stocklens.research.ai;

public record AiComparisonPrompt(String systemMessage, String userMessage) {
    public AiComparisonPrompt withRepair(String issues) {
        String sourceCountCorrection = issues.contains("too many source IDs")
                ? """
                  The previous output exceeded 15 unique source IDs. Retain only the strongest citations,
                  remove weak or redundant citations, and preserve the existing analysis.
                  """
                : "";
        return new AiComparisonPrompt(systemMessage, userMessage
                + "\n\nREPAIR REQUIRED. Validation failures: " + issues + ".\n"
                + sourceCountCorrection
                + "Correct only the structured response. Use only supplied source IDs and no new facts. "
                + "Return corrected JSON only.");
    }
}
