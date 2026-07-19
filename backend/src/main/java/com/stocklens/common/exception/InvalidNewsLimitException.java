package com.stocklens.common.exception;

public class InvalidNewsLimitException extends RuntimeException {

    public InvalidNewsLimitException() {
        super("News limit must be between 1 and 20.");
    }
}
