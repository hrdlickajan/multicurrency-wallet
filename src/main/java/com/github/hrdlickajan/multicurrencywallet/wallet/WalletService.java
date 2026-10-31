package com.github.hrdlickajan.multicurrencywallet.wallet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.hrdlickajan.multicurrencywallet.rates.RateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final Map<Currency, Money> balances = new HashMap<>();
    private final RateService rateService;

    public Money deposit(Money money) {
        return changeBalance(money, false);
    }

    public Money withdraw(Money money) {
        return changeBalance(money, true);
    }

    private synchronized Money changeBalance(Money money, boolean withdrawal) {
        if (money.amount().signum() <= 0) {
            throw new InvalidMoneyException("Transaction amount must be positive.");
        }

        var code = money.currency().getCurrencyCode();
        var currentBalance = balances.getOrDefault(money.currency(), new Money(BigDecimal.ZERO, money.currency()));
        if (withdrawal && currentBalance.amount().compareTo(money.amount()) < 0) {
            throw new InsufficientFundsException("Insufficient funds in " + code + ".");
        }
        var amount = withdrawal
            ? currentBalance.amount().subtract(money.amount())
            : currentBalance.amount().add(money.amount());
        var balance = new Money(amount, money.currency());
        balances.put(money.currency(), balance);
        return balance;
    }

    public synchronized List<Money> balances() {
        return balances.values().stream()
                .sorted(Comparator.comparing(money -> money.currency().getCurrencyCode()))
                .toList();
    }

    public WalletTotal calculateTotal(Currency currency) {
        var allBalances = balances();
        var rates = rateService.getRates();
        var targetRate = rates.table().getRateFor(currency);
        var totalInCzk = allBalances.stream()
                .filter(holding -> holding.amount().signum() != 0)
            .map(holding -> holding.amount().multiply(rates.table().getRateFor(holding.currency())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var total = new Money(totalInCzk.divide(targetRate, currency.getDefaultFractionDigits(), RoundingMode.HALF_EVEN),
                currency);
        return new WalletTotal(total, rates.stale());
    }
}