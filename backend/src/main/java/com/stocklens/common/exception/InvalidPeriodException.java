package com.stocklens.common.exception;

public class InvalidPeriodException extends RuntimeException {

    public InvalidPeriodException() {
        super("Period must be one of 1M, 6M, 1Y, 5Y, or MAX.");
    }
}
