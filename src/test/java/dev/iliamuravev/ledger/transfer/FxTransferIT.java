package dev.iliamuravev.ledger.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.iliamuravev.ledger.reconciliation.ReconciliationService;
import dev.iliamuravev.ledger.support.IntegrationTest;
import dev.iliamuravev.ledger.support.LedgerApi;
import dev.iliamuravev.ledger.support.LedgerQueries;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

class FxTransferIT extends IntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Autowired
    LedgerQueries queries;

    @Autowired
    ReconciliationService reconciliation;

    LedgerApi api;

    @BeforeEach
    void setUp() {
        api = new LedgerApi(http);
    }

    @Test
    void convertsThroughFxPositionAccountsWithFourEntries() {
        var eur = api.fundedAccount("EUR", 50_000);
        var usd = api.openAccountId("USD");

        var response = api.fxTransfer(key(), eur, usd, 10_000, "EUR", "USD");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var body = response.getBody();
        assertThat(body.get("amount").asLong()).isEqualTo(10_000);
        assertThat(body.get("currency").asText()).isEqualTo("EUR");
        assertThat(body.get("fx").get("targetAmount").asLong()).isEqualTo(10_850);
        assertThat(body.get("fx").get("targetCurrency").asText()).isEqualTo("USD");
        assertThat(body.get("fx").get("rate").decimalValue()).isEqualByComparingTo("1.085");
        assertThat(body.get("fx").get("rateSource").asText()).isEqualTo("static-config");
        assertThat(body.get("fx").hasNonNull("quotedAt")).isTrue();

        var transferId = UUID.fromString(body.get("id").asText());
        var eurPosition = queries.fxPositionAccount("EUR");
        var usdPosition = queries.fxPositionAccount("USD");
        assertThat(queries.entriesOfTransfer(transferId)).containsExactlyInAnyOrder(
                eur + " -10000 EUR",
                eurPosition + " 10000 EUR",
                usdPosition + " -10850 USD",
                usd + " 10850 USD");
        assertThat(api.balance(eur)).isEqualTo(40_000);
        assertThat(api.balance(usd)).isEqualTo(10_850);
        assertThat(reconciliation.run().consistent()).isTrue();
    }

    @Test
    void replayReturnsTheOriginalRateSnapshot() {
        var eur = api.fundedAccount("EUR", 5_000);
        var jpy = api.openAccountId("JPY");
        var key = key();

        var first = api.fxTransfer(key, eur, jpy, 1_000, "EUR", "JPY");
        var second = api.fxTransfer(key, eur, jpy, 1_000, "EUR", "JPY");

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().get("fx")).isEqualTo(first.getBody().get("fx"));
        assertThat(api.getTransfer(first.getBody().get("id").asText()).getBody().get("fx"))
                .isEqualTo(first.getBody().get("fx"));
        assertThat(api.balance(jpy)).isEqualTo(first.getBody().get("fx").get("targetAmount").asLong());
    }

    @Test
    void sameKeyWithAnotherTargetCurrencyIs422() {
        var eur = api.fundedAccount("EUR", 5_000);
        var usd = api.openAccountId("USD");
        var key = key();
        api.fxTransfer(key, eur, usd, 100, "EUR", "USD");

        assertThat(api.transfer(key, eur, usd, 100, "EUR").getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void crossCurrencyWithoutTargetIsStillACurrencyMismatch() {
        var eur = api.fundedAccount("EUR", 5_000);
        var usd = api.openAccountId("USD");

        var response = api.transfer(key(), eur, usd, 100, "EUR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("reason").asText()).isEqualTo("CURRENCY_MISMATCH");
    }

    @Test
    void targetMustMatchTheDestinationAccount() {
        var eur = api.fundedAccount("EUR", 5_000);
        var gbp = api.openAccountId("GBP");

        var response = api.fxTransfer(key(), eur, gbp, 100, "EUR", "USD");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("reason").asText()).isEqualTo("CURRENCY_MISMATCH");
    }

    @Test
    void pairWithoutARateIsRejected() {
        var eur = api.fundedAccount("EUR", 5_000);
        var sek = api.openAccountId("SEK");

        var response = api.fxTransfer(key(), eur, sek, 100, "EUR", "SEK");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("type").asText()).isEqualTo("/problems/fx-rate-unavailable");
        assertThat(api.balance(eur)).isEqualTo(5_000);
    }

    @Test
    void insufficientFundsIsCheckedInTheSourceCurrency() {
        var eur = api.fundedAccount("EUR", 999);
        var usd = api.openAccountId("USD");

        var response = api.fxTransfer(key(), eur, usd, 1_000, "EUR", "USD");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("reason").asText()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void amountThatConvertsToNothingIs400AndKeepsTheKeyFree() {
        var jpy = api.fundedAccount("JPY", 10);
        var gbp = api.openAccountId("GBP");
        var usd = api.openAccountId("USD");
        var key = key();

        assertThat(api.fxTransfer(key, jpy, gbp, 0, "JPY", "GBP").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(api.fxTransfer(key, jpy, usd, 1, "JPY", "USD").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void statementShowsTheCustomerCounterpartyOnBothSides() {
        var eur = api.fundedAccount("EUR", 5_000);
        var usd = api.openAccountId("USD");
        api.fxTransfer(key(), eur, usd, 1_000, "EUR", "USD");

        var statement = await().atMost(Duration.ofSeconds(20))
                .until(() -> api.get("/accounts/{id}/statement", usd).getBody(), s -> s.get("items").size() == 1);

        var line = statement.get("items").get(0);
        assertThat(line.get("counterpartyAccountId").asText()).isEqualTo(eur.toString());
        assertThat(line.get("amount").asLong()).isEqualTo(1_085);
        assertThat(line.get("currency").asText()).isEqualTo("USD");
    }

    @Test
    void ratesAreQueryable() {
        var response = api.get("/fx/rates?from=EUR&to=USD");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("rate").decimalValue()).isEqualByComparingTo(new BigDecimal("1.085"));
        assertThat(api.get("/fx/rates?from=EUR&to=SEK").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }
}
