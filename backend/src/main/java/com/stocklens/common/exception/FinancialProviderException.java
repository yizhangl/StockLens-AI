package com.stocklens.common.exception;

public class FinancialProviderException extends RuntimeException {

    public FinancialProviderException(String message) {
        super(message);
    }

    public FinancialProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
