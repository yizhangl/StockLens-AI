package com.stocklens.common.exception;

public class StockNotFoundException extends RuntimeException {

    public StockNotFoundException(String ticker) {
        super("No company was found for ticker " + ticker + ".");
    }
}
