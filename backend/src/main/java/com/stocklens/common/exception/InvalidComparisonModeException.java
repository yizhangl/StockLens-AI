package com.stocklens.common.exception;

public class InvalidComparisonModeException extends RuntimeException {

    public InvalidComparisonModeException() {
        super("Comparison mode must be PRICE or RETURN.");
    }
}
