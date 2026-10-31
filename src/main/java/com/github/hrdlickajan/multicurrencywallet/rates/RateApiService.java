package com.github.hrdlickajan.multicurrencywallet.rates;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateApiService {

    private final RestClient cnbClient;
    private final Clock clock;

    public RateTable fetch() {
        return normalizeRates(callCnb());
    }

    private CnbResponse callCnb() {
        try {
            return cnbClient.get()
                    .uri(uri -> uri.queryParam("date", LocalDate.now(clock))
                            .queryParam("lang", "EN").build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(status -> status.value() != 200, (_, response) -> {
                        throw new RestClientException("CNB returned HTTP " + response.getStatusCode().value() + ".");
                    })
                    .body(CnbResponse.class);
        } catch (RestClientException exception) {
            throw new RatesUnavailableException("CNB rates are currently unavailable.", exception);
        }
    }

    private RateTable normalizeRates(CnbResponse response) {
        if (response == null || response.rates() == null) {
            throw new RatesUnavailableException("CNB response could not be read as a rate table.");
        }
        var validRates = response.rates().stream()
                .map(this::normalizeRow)
                .filter(Objects::nonNull)
                .toList();
        if (CollectionUtils.isEmpty(validRates)) {
            throw new RatesUnavailableException("CNB response could not be read as a rate table.");
        }
        var date = validRates.getFirst().date();
        var normalizedRates = validRates.stream()
                .filter(rate -> date.equals(rate.date()))
                .collect(Collectors.toMap(NormalizedRate::currency, NormalizedRate::czkPerUnit,
                        (_, replacement) -> replacement));
        normalizedRates.put(Currency.getInstance("CZK"), BigDecimal.ONE);
        return new RateTable(date, normalizedRates);
    }

    private NormalizedRate normalizeRow(CnbRate rate) {
        try {
            var currency = Currency.getInstance(rate.currencyCode());
            if (rate.validFor() == null || rate.amount().signum() <= 0 || rate.rate().signum() <= 0) {
                throw new IllegalArgumentException("CNB rates must have a date and positive units and values.");
            }
            return new NormalizedRate(rate.validFor(), currency, rate.rate().divide(rate.amount(), RoundingMode.UNNECESSARY));
        } catch (IllegalArgumentException | ArithmeticException | NullPointerException _) {
            log.warn("Skipping invalid or unknown CNB rate: {}", rate);
            return null;
        }
    }

    private record NormalizedRate(LocalDate date, Currency currency, BigDecimal czkPerUnit) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CnbResponse(List<CnbRate> rates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CnbRate(LocalDate validFor, BigDecimal amount, String currencyCode, BigDecimal rate) {
    }
}