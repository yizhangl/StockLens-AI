package com.stocklens.research.ai;

public class AiGenerationUnavailableException extends RuntimeException {
    public AiGenerationUnavailableException() { super("AI comparison generation is not configured."); }
}
