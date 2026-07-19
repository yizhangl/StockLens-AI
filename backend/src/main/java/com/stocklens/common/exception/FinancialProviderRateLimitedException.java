package com.stocklens.common.exception;

public class FinancialProviderRateLimitedException extends FinancialProviderException {

    private final Long retryAfterSeconds;

    public FinancialProviderRateLimitedException(Long retryAfterSeconds) {
        super("The financial data provider rate limit was reached.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
