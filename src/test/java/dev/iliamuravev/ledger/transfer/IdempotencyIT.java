package dev.iliamuravev.ledger.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.iliamuravev.ledger.support.IntegrationTest;
import dev.iliamuravev.ledger.support.LedgerApi;
import dev.iliamuravev.ledger.support.LedgerQueries;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

class IdempotencyIT extends IntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Autowired
    LedgerQueries queries;

    LedgerApi api;

    @BeforeEach
    void setUp() {
        api = new LedgerApi(http);
    }

    @Test
    void repeatWithSameKeyAndBodyReturnsTheOriginalTransferWithoutMovingMoneyAgain() {
        var from = api.fundedAccount("EUR", 1_000);
        var to = api.openAccountId("EUR");
        var key = UUID.randomUUID().toString();

        var first = api.transfer(key, from, to, 300, "EUR");
        var second = api.transfer(key, from, to, 300, "EUR");

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).isEqualTo(first.getBody());
        assertThat(second.getHeaders().getLocation()).isEqualTo(first.getHeaders().getLocation());
        assertThat(api.balance(from)).isEqualTo(700);
        assertThat(api.balance(to)).isEqualTo(300);
        assertThat(queries.transfersWithKey(key)).isOne();
    }

    @Test
    void sameKeyWithDifferentBodyIs422() {
        var from = api.fundedAccount("EUR", 1_000);
        var to = api.openAccountId("EUR");
        var key = UUID.randomUUID().toString();
        api.transfer(key, from, to, 300, "EUR");

        var response = api.transfer(key, from, to, 301, "EUR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().get("type").asText()).isEqualTo("/problems/idempotency-key-reused");
        assertThat(api.balance(from)).isEqualTo(700);
    }

    @Test
    void keyIsGlobalAcrossDepositsAndTransfers() {
        var from = api.fundedAccount("EUR", 1_000);
        var to = api.openAccountId("EUR");
        var key = UUID.randomUUID().toString();
        api.deposit(key, to, 50, "EUR");

        var response = api.transfer(key, from, to, 50, "EUR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void rejectedTransferIsReplayedAsTheSameRejection() {
        var from = api.fundedAccount("EUR", 100);
        var to = api.openAccountId("EUR");
        var key = UUID.randomUUID().toString();

        var first = api.transfer(key, from, to, 500, "EUR");
        api.fund(from, 1_000, "EUR");
        var second = api.transfer(key, from, to, 500, "EUR");

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().get("transferId")).isEqualTo(first.getBody().get("transferId"));
        assertThat(api.balance(to)).isZero();
    }

    @Test
    void notFoundDoesNotConsumeTheKey() {
        var from = api.fundedAccount("EUR", 100);
        var to = api.openAccountId("EUR");
        var key = UUID.randomUUID().toString();

        assertThat(api.transfer(key, from, UUID.randomUUID(), 10, "EUR").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(api.transfer(key, from, to, 10, "EUR").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void headerIsRequired() {
        var from = api.fundedAccount("EUR", 100);
        var to = api.openAccountId("EUR");

        var response = api.post("/transfers", null, Map.of(
                "fromAccountId", from.toString(), "toAccountId", to.toString(), "amount", 1, "currency", "EUR"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("detail").asText()).contains("Idempotency-Key");
    }

    @Test
    void oversizedKeyIs400() {
        var from = api.fundedAccount("EUR", 100);
        var to = api.openAccountId("EUR");

        var response = api.transfer("k".repeat(256), from, to, 1, "EUR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
