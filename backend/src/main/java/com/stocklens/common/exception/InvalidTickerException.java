package com.stocklens.common.exception;

import com.stocklens.common.validation.TickerNormalizer;

public class InvalidTickerException extends RuntimeException {

    public InvalidTickerException() {
        super("Ticker must match " + TickerNormalizer.SUPPORTED_FORMAT + ".");
    }
}
