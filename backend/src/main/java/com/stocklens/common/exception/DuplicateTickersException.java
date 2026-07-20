package com.stocklens.common.exception;

public class DuplicateTickersException extends RuntimeException {

    public DuplicateTickersException() {
        super("Left and right tickers must be different.");
    }
}
