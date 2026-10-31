package com.github.hrdlickajan.multicurrencywallet.rates;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateService {

    private static final String CACHE_KEY = "rates";

    private final RateApiService rateApiService;
    private final Clock clock;
    private final Cache<String, CachedRates> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofDays(7))
            .build();
    private volatile boolean latestRefreshSucceeded = true;

    public Rates getRates() {
        var cachedRates = cache.getIfPresent(CACHE_KEY);
        if (cachedRates == null) {
            cachedRates = fetchAndCacheRates();
        }
        return new Rates(cachedRates.table(), !latestRefreshSucceeded);
    }

    @Scheduled(cron = "${wallet.rates.refresh-cron:0 40 14 * * *}", zone = "Europe/Prague")
    public void refresh() {
        try {
            fetchAndCacheRates();
        } catch (RatesUnavailableException exception) {
            log.warn("Scheduled CNB refresh failed; no usable cached rates are available", exception);
        }
    }

    private CachedRates fetchAndCacheRates() {
        try {
            var loaded = new CachedRates(rateApiService.fetch(), clock.instant());
            cache.put(CACHE_KEY, loaded);
            latestRefreshSucceeded = true;
            return loaded;
        } catch (RatesUnavailableException exception) {
            latestRefreshSucceeded = false;
            var cachedRates = cache.getIfPresent(CACHE_KEY);
            if (cachedRates != null) {
                log.warn("CNB refresh failed; using rates dated {} fetched at {}",
                        cachedRates.table().date(), cachedRates.fetchedAt(), exception);
                return cachedRates;
            }
            throw exception;
        }
    }

    private record CachedRates(RateTable table, Instant fetchedAt) {
    }

    public record Rates(RateTable table, boolean stale) {
    }
}