package com.github.hrdlickajan.multicurrencywallet.wallet;

public class InvalidMoneyException extends RuntimeException {

    public InvalidMoneyException(String message) {
        super(message);
    }
}