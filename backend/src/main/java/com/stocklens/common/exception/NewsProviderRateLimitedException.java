package com.stocklens.common.exception;

public class NewsProviderRateLimitedException extends NewsProviderException {

    private final Long retryAfterSeconds;

    public NewsProviderRateLimitedException(Long retryAfterSeconds) {
        super("The news provider rate limit was reached.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
