package com.github.hrdlickajan.multicurrencywallet.rates;

public class RatesUnavailableException extends RuntimeException {

    public RatesUnavailableException(String message) {
        super(message);
    }

    public RatesUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}