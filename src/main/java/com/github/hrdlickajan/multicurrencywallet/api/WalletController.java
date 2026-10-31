package com.github.hrdlickajan.multicurrencywallet.api;

import java.util.List;

import com.github.hrdlickajan.multicurrencywallet.api.generated.WalletApi;
import com.github.hrdlickajan.multicurrencywallet.api.generated.model.Currency;
import com.github.hrdlickajan.multicurrencywallet.api.generated.model.MoneyRequest;
import com.github.hrdlickajan.multicurrencywallet.api.generated.model.MoneyResponse;
import com.github.hrdlickajan.multicurrencywallet.api.generated.model.RateStatus;
import com.github.hrdlickajan.multicurrencywallet.api.generated.model.TotalBalanceResponse;
import com.github.hrdlickajan.multicurrencywallet.wallet.Money;
import com.github.hrdlickajan.multicurrencywallet.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class WalletController implements WalletApi {

    private final WalletService walletService;

    @Override
    public MoneyResponse deposit(MoneyRequest request) {
        return toResponse(walletService.deposit(Money.of(request.getAmount(), request.getCurrency())));
    }

    @Override
    public MoneyResponse withdraw(MoneyRequest request) {
        return toResponse(walletService.withdraw(Money.of(request.getAmount(), request.getCurrency())));
    }

    @Override
    public List<MoneyResponse> balances() {
        return walletService.balances().stream().map(WalletController::toResponse).toList();
    }

    @Override
    public TotalBalanceResponse total(String currency) {
        var total = walletService.calculateTotal(Money.parseSupportedCurrency(currency));
        var money = toResponse(total.money());
        return new TotalBalanceResponse(money.getAmount(), money.getCurrency(),
            total.stale() ? RateStatus.STALE : RateStatus.LATEST_AVAILABLE);
    }

    private static MoneyResponse toResponse(Money money) {
        return new MoneyResponse(money.amount().toPlainString(), Currency.fromValue(money.currency().getCurrencyCode()));
    }
}