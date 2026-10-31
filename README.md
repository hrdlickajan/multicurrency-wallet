# Multicurrency Wallet

Java 25 / Spring Boot REST API for one in-memory account. Restarting clears all data.

## Build and Run

Requires JDK 25 (`JAVA_HOME` and `PATH`). From the repository root:

```sh
./mvnw clean package
java -jar target/multicurrency-wallet-0.0.1-SNAPSHOT.jar
```

Using IntelliJ/VSCode:

- Maven clean + Install
- Run

API: `http://localhost:8080`. Windows: use `mvnw.cmd` instead of `./mvnw`.
Tests: `./mvnw test`. No separate Maven installation or database is needed.

## API

| Request                            | Result                                                               |
|------------------------------------|----------------------------------------------------------------------|
| `POST /deposits`                   | Balance after depositing                                             |
| `POST /withdrawals`                | Balance after withdrawing from that currency only                    |
| `GET /balances`                    | Stored balances, including zero balances                             |
| `GET /balances/total?currency=USD` | Total `amount`, `currency`, and `rateStatus`; balances are unchanged |

POST body: `{"amount":"10","currency":"USD"}`.
Currency codes are case-insensitive; supported codes are listed in the OpenAPI source (XDR excluded).

[OpenAPI source](src/main/resources/static/openapi.json) generates the API interface and DTOs
during the build. Served at `/openapi.json`; Springdoc at `/v3/api-docs`.
Examples: [Bruno collection](bruno/bruno.json).

## Assignment Decisions

1. **Missing daily rates:** Request today's Prague date and accept CNB's latest publication on
   weekends and holidays. `rateStatus` is `LATEST_AVAILABLE` when the last refresh succeeded and
   `STALE` when it failed and cached rates are used.
2. **Units:** Divide each CNB quotation by its unit count (e.g. 100 JPY), without rounding.
3. **CNB unavailable:** Fetch on cache miss and daily at 14:40 Prague time. A table expires seven
   days after a successful fetch. On failure, reuse an unexpired table or return 503; a failure
   does not extend expiry. Deposits and withdrawals do not need CNB.
4. **Precision:** `BigDecimal` with each currency's fraction digits. Excess input precision is
   rejected. Totals sum exact CZK values and round once with `HALF_EVEN`. Responses use decimal
   strings.
5. **Concurrency:** One synchronized wallet lock makes withdrawals atomic. Rate fetching and conversion happen outside
   it.
6. **Errors:** Problem Details JSON with a `code`: 400 invalid input or currency, 409 insufficient
   funds, 503 missing or unavailable rates (the message names the currency), 500 unexpected failure.
7. **CZK:** Rate is exactly 1. Totals still need a usable CNB table; zero holdings need no rate.

Local demo only: no authentication or durable storage.

See also [AI_USAGE.md](AI_USAGE.md)