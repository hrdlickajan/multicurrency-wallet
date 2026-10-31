package com.github.hrdlickajan.multicurrencywallet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import com.github.hrdlickajan.multicurrencywallet.wallet.InvalidMoneyException;
import com.github.hrdlickajan.multicurrencywallet.wallet.Money;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("java:S5778")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "wallet.rates.refresh-cron=-")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MulticurrencyWalletApplicationTests {

    private static final String RATE_DATE = "2026-10-23";
    private static final HttpServer CNB = startCnb();

    private final JsonMapper json = JsonMapper.builder().build();

    @Value("${local.server.port}")
    private int port;

    @DynamicPropertySource
    static void cnbEndpoint(DynamicPropertyRegistry registry) {
        registry.add("wallet.cnb.url", () -> "http://127.0.0.1:" + CNB.getAddress().getPort()
                + "/cnbapi/exrates/daily");
    }

    @AfterAll
    static void stopCnb() {
        CNB.stop(0);
    }

    @Test
    void supportedCurrenciesMatchCnbFixture() throws IOException {
        var expected = new TreeSet<String>();
        json.readTree(Files.readAllBytes(Path.of("example_response.json"))).get("rates").forEach(rate -> {
            var code = rate.get("currencyCode").asText();
            if (!code.equals("XDR")) {
                expected.add(code);
            }
        });
        expected.add("CZK");

        var supported = new TreeSet<String>();
        for (var currency : Currency.getAvailableCurrencies()) {
            try {
                Money.parseSupportedCurrency(currency.getCurrencyCode());
            } catch (InvalidMoneyException _) {
                continue;
            }
            supported.add(currency.getCurrencyCode());
        }
        assertThat(supported).containsExactlyElementsOf(expected);
        expected.forEach(code -> assertThat(Money.parse("1", code.toLowerCase(Locale.ROOT)).currency())
                .isEqualTo(Currency.getInstance(code)));
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, Currency.getInstance("XDR")))
                .isInstanceOf(InvalidMoneyException.class);
        assertThatThrownBy(() -> Money.parse("1", "XDR")).isInstanceOf(InvalidMoneyException.class);
    }

    @Test
    void depositWithdrawAndValueTheAccountThroughHttp() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var empty = send(client, "/balances", null);
            assertThat(empty.statusCode()).isEqualTo(200);
            assertThat(empty.body()).isEqualTo("[]");

            assertObject(send(client, "/deposits", """
                    {"amount":"10","currency":"usd"}
                    """), Map.of("amount", "10.00", "currency", "USD"));
            assertObject(send(client, "/deposits", """
                    {"amount":"5","currency":"EUR"}
                    """), Map.of("amount", "5.00", "currency", "EUR"));
            assertObject(send(client, "/withdrawals", """
                    {"amount":"3","currency":"USD"}
                    """), Map.of("amount", "7.00", "currency", "USD"));

            assertObject(send(client, "/balances/total?currency=USD", null),
                    Map.of("amount", "12.57", "currency", "USD", "rateStatus", "LATEST_AVAILABLE"));
            assertObject(send(client, "/balances/total?currency=EUR", null),
                    Map.of("amount", "11.28", "currency", "EUR", "rateStatus", "LATEST_AVAILABLE"));

            var holdings = send(client, "/balances", null);
            assertThat(holdings.statusCode()).isEqualTo(200);
            assertThat(json.readValue(holdings.body(), new TypeReference<List<Map<String, String>>>() { }))
                    .containsExactlyInAnyOrder(Map.of("amount", "7.00", "currency", "USD"),
                            Map.of("amount", "5.00", "currency", "EUR"));

            assertObject(send(client, "/deposits", """
                    {"amount":"1000000000.00","currency":"CZK"}
                    """), Map.of("amount", "1000000000.00", "currency", "CZK"));
            assertThat(json.readValue(send(client, "/balances", null).body(),
                    new TypeReference<List<Map<String, String>>>() { }))
                    .contains(Map.of("amount", "1000000000.00", "currency", "CZK"));
            assertObject(send(client, "/withdrawals", """
                    {"amount":"1000000000.00","currency":"CZK"}
                    """), Map.of("amount", "0.00", "currency", "CZK"));
            assertObject(send(client, "/deposits", """
                    {"amount":1,"currency":"USD"}
                    """), Map.of("amount", "8.00", "currency", "USD"));
            assertObject(send(client, "/withdrawals", """
                    {"amount":1,"currency":"USD"}
                    """), Map.of("amount", "7.00", "currency", "USD"));
            assertObject(send(client, "/deposits", """
                    {"amount":1e3,"currency":"CZK"}
                    """), Map.of("amount", "1000.00", "currency", "CZK"));
            assertObject(send(client, "/withdrawals", """
                    {"amount":"1E+3","currency":"CZK"}
                    """), Map.of("amount", "0.00", "currency", "CZK"));
            var beforeRejections = json.readValue(send(client, "/balances", null).body(),
                    new TypeReference<List<Map<String, String>>>() { });
            assertThat(beforeRejections).containsExactlyInAnyOrder(
                    Map.of("amount", "7.00", "currency", "USD"),
                    Map.of("amount", "5.00", "currency", "EUR"),
                    Map.of("amount", "0.00", "currency", "CZK"));

            assertProblem(send(client, "/withdrawals", """
                    {"amount":"8","currency":"USD"}
                    """), 409, "INSUFFICIENT_FUNDS");
            for (var invalid : List.of(
                    """
                    {"amount":"1","currency":"ZZZ"}
                    """,
                    """
                    {"amount":"1","currency":"XDR"}
                    """,
                    """
                    {"amount":"1.001","currency":"USD"}
                        """,
                        """
                        {"amount":1.5,"currency":"JPY"}
                        """,
                        """
                        {"amount":0,"currency":"USD"}
                        """,
                        """
                        {"amount":-1,"currency":"USD"}
                    """)) {
                assertProblem(send(client, "/deposits", invalid), 400, "INVALID_MONEY");
                    }
                    assertProblem(send(client, "/deposits", """
                        {"amount":true,"currency":"USD"}
                    """), 400, "INVALID_REQUEST");
                    assertProblem(send(client, "/deposits", """
                        {"currency":"USD"}
                        """), 400, "INVALID_REQUEST");
                    assertProblem(send(client, "/deposits", """
                        {"amount":null,"currency":"USD"}
                        """), 400, "INVALID_REQUEST");
                    assertProblem(send(client, "/balances/total?currency=XDR", null), 400, "INVALID_MONEY");
                    assertThat(json.readValue(send(client, "/balances", null).body(),
                    new TypeReference<List<Map<String, String>>>() { }))
                    .containsExactlyInAnyOrderElementsOf(beforeRejections);
        }
    }

    private HttpResponse<String> send(HttpClient client, String path, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10));
        if (body != null) {
            request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertObject(HttpResponse<String> response, Map<String, String> expected) {
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
        assertThat(MediaType.parseMediaType(response.headers().firstValue("Content-Type").orElseThrow())
            .isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        assertThat(json.readTree(response.body())).isEqualTo(json.valueToTree(expected));
    }

    private void assertProblem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(status);
        assertThat(MediaType.parseMediaType(response.headers().firstValue("Content-Type").orElseThrow())
            .isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
        assertThat(json.readValue(response.body(), new TypeReference<Map<String, Object>>() { }))
            .containsKeys("title", "status", "code").containsEntry("status", status).containsEntry("code", code);
    }

    private static HttpServer startCnb() {
        try {
            var mapper = JsonMapper.builder().build();
            var response = mapper.readTree(Files.readAllBytes(Path.of("example_response.json")));
            response.get("rates").forEach(rate -> ((ObjectNode) rate).put("validFor", RATE_DATE));
            var fixture = mapper.writeValueAsBytes(response);
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/cnbapi/exrates/daily", exchange -> {
                try (exchange) {
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, fixture.length);
                    exchange.getResponseBody().write(fixture);
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
