package com.github.hrdlickajan.multicurrencywallet.rates;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SuppressWarnings("java:S5778")
class RateApiServiceTests {

    @Test
    void skipsInvalidAndUnknownRowsBeforeChoosingDate() {
        var service = serviceReturning("""
                {"rates":[
                    null,
                    {},
                    {"currencyCode":"ZZZ","validFor":"2026-10-22","amount":1,"rate":1},
                    {"currencyCode":"EUR","validFor":null,"amount":1,"rate":1},
                    {"currencyCode":"EUR","validFor":"2026-10-22","amount":null,"rate":1},
                    {"currencyCode":"EUR","validFor":"2026-10-22","amount":1,"rate":null},
                    {"currencyCode":"EUR","validFor":"2026-10-22","amount":0,"rate":1},
                    {"currencyCode":"EUR","validFor":"2026-10-22","amount":-1,"rate":1},
                    {"currencyCode":"EUR","validFor":"2026-10-22","amount":1,"rate":0},
                    {"currencyCode":"EUR","validFor":"2026-10-22","amount":1,"rate":-1},
                    {"currencyCode":"EUR","validFor":"2026-10-22","amount":3,"rate":1},
                    {"currencyCode":"USD","validFor":"2026-10-23","amount":1,"rate":21.834},
                    {"currencyCode":"EUR","validFor":"2026-10-24","amount":1,"rate":25},
                    {"currencyCode":"JPY","validFor":"2026-10-23","amount":100,"rate":13.835}
                ]}
                """);

        var table = service.fetch();

        assertThat(table.date()).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(table.czkPerUnit()).containsOnlyKeys(
                Currency.getInstance("CZK"), Currency.getInstance("USD"), Currency.getInstance("JPY"));
        assertThat(table.getRateFor(Currency.getInstance("CZK"))).isEqualByComparingTo("1");
        assertThat(table.getRateFor(Currency.getInstance("USD"))).isEqualByComparingTo("21.834");
        assertThat(table.getRateFor(Currency.getInstance("JPY"))).isEqualByComparingTo("0.13835");
        assertThatThrownBy(() -> table.getRateFor(Currency.getInstance("EUR")))
                .isInstanceOf(RatesUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "null", "{}", "{\"rates\":null}", "{\"rates\":[]}", "{\"rates\":[null]}",
            "{\"rates\":[{\"currencyCode\":\"ZZZ\"}]}",
            "{\"rates\":[{\"currencyCode\":\"USD\",\"validFor\":\"2026-10-23\",\"amount\":0,\"rate\":1}]}",
            "{\"rates\":[{\"currencyCode\":\"USD\",\"validFor\":\"not-a-date\",\"amount\":1,\"rate\":1}]}"
    })
    void rejectsResponsesWithoutUsableRates(String body) {
        assertThatThrownBy(() -> serviceReturning(body).fetch()).isInstanceOf(RatesUnavailableException.class);
    }

    private RateApiService serviceReturning(String body) {
        var builder = RestClient.builder().baseUrl("http://cnb.test/daily");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://cnb.test/daily?date=2026-10-31&lang=EN"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        var clock = Clock.fixed(Instant.parse("2026-10-31T10:00:00Z"), ZoneId.of("Europe/Prague"));
        return new RateApiService(builder.build(), clock);
    }
}