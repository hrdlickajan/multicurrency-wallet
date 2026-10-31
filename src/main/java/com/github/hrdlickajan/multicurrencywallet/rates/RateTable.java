package com.github.hrdlickajan.multicurrencywallet.rates;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Map;

public record RateTable(LocalDate date, Map<Currency, BigDecimal> czkPerUnit) {

    public RateTable {
        czkPerUnit = Map.copyOf(czkPerUnit);
    }

    public BigDecimal getRateFor(Currency currency) {
        var rate = czkPerUnit.get(currency);
        if (rate == null) {
            throw new RatesUnavailableException("Missing CNB rate for " + currency.getCurrencyCode() + ".");
        }
        return rate;
    }
}