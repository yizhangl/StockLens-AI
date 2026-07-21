package com.stocklens.research.ai;

public class AiRateLimitedException extends RuntimeException {
    public AiRateLimitedException(Throwable cause) { super("AI provider rate limited", cause); }
}
