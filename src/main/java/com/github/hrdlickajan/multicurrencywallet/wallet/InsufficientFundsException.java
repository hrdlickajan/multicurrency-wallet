package com.github.hrdlickajan.multicurrencywallet.wallet;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String message) {
        super(message);
    }
}