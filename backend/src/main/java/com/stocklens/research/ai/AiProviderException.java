package com.stocklens.research.ai;

public class AiProviderException extends RuntimeException {
    public AiProviderException(Throwable cause) { super("AI provider request failed", cause); }
}
