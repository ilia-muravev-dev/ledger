package dev.iliamuravev.ledger.account;

import static org.assertj.core.api.Assertions.assertThat;

import dev.iliamuravev.ledger.support.IntegrationTest;
import dev.iliamuravev.ledger.support.LedgerApi;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

class AccountLifecycleIT extends IntegrationTest {

    @Autowired
    TestRestTemplate http;

    LedgerApi api;

    @BeforeEach
    void setUp() {
        api = new LedgerApi(http);
    }

    @Test
    void depositCreditsTheAccountFromTheSettlementAccount() {
        var account = api.openAccountId("GBP");

        var response = api.deposit(UUID.randomUUID().toString(), account, 12_345, "GBP");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("type").asText()).isEqualTo("DEPOSIT");
        assertThat(response.getBody().get("toAccountId").asText()).isEqualTo(account.toString());
        assertThat(api.balance(account)).isEqualTo(12_345);

        var settlement = api.getAccount(response.getBody().get("fromAccountId").asText()).getBody();
        assertThat(settlement.get("type").asText()).isEqualTo("SETTLEMENT");
        assertThat(settlement.get("currency").asText()).isEqualTo("GBP");
        assertThat(settlement.get("balance").asLong()).isNegative();
    }

    @Test
    void emptyAccountCanBeClosedAndClosingIsIdempotent() {
        var account = api.openAccountId("EUR");

        var first = api.closeAccount(account);
        var second = api.closeAccount(account);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().get("status").asText()).isEqualTo("CLOSED");
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void accountWithMoneyCannotBeClosed() {
        var account = api.fundedAccount("EUR", 1);

        var response = api.closeAccount(account);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("type").asText()).isEqualTo("/problems/account-not-empty");
    }

    @Test
    void depositIntoClosedAccountIsRejected() {
        var account = api.openAccountId("EUR");
        api.closeAccount(account);

        var response = api.deposit(UUID.randomUUID().toString(), account, 100, "EUR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("reason").asText()).isEqualTo("ACCOUNT_CLOSED");
    }

    @Test
    void closingUnknownAccountIs404() {
        assertThat(api.closeAccount(UUID.randomUUID()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
