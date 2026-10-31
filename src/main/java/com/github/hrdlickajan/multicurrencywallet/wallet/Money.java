package com.github.hrdlickajan.multicurrencywallet.wallet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public record Money(BigDecimal amount, Currency currency) {

    private static final Set<String> SUPPORTED_CODES = Set.of(
            "AUD", "BRL", "CAD", "CHF", "CNY", "CZK", "DKK", "EUR", "GBP", "HKD",
            "HUF", "IDR", "ILS", "INR", "ISK", "JPY", "KRW", "MXN", "MYR", "NOK",
            "NZD", "PHP", "PLN", "RON", "SEK", "SGD", "THB", "TRY", "USD", "ZAR");
    private static final Pattern DECIMAL = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    public Money {
        if (amount == null || currency == null) {
            throw new InvalidMoneyException("Amount and currency are required.");
        }
        validateCurrency(currency);
        if (amount.signum() < 0) {
            throw new InvalidMoneyException("Money cannot be negative.");
        }
        try {
            amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.UNNECESSARY);
        } catch (ArithmeticException _) {
            throw new InvalidMoneyException("Amount exceeds the currency's fractional precision.");
        }
    }

    public static Money parse(String amount, String currency) {
        if (amount == null || !DECIMAL.matcher(amount).matches()) {
            throw new InvalidMoneyException("Amount must be a plain dot-separated decimal string.");
        }
        return of(new BigDecimal(amount), currency);
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, parseCurrency(currency));
    }

    public static Currency parseSupportedCurrency(String code) {
        var currency = parseCurrency(code);
        validateCurrency(currency);
        return currency;
    }

    private static Currency parseCurrency(String code) {
        if (code == null) {
            throw new InvalidMoneyException("Currency is required.");
        }
        try {
            return Currency.getInstance(code.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            throw new InvalidMoneyException("Invalid currency code.");
        }
    }

    private static void validateCurrency(Currency currency) {
        if (!SUPPORTED_CODES.contains(currency.getCurrencyCode())) {
            throw new InvalidMoneyException("Unsupported currency: " + currency.getCurrencyCode());
        }
    }
}