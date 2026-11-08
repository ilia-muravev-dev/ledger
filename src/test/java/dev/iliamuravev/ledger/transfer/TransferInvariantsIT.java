package dev.iliamuravev.ledger.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.iliamuravev.ledger.support.IntegrationTest;
import dev.iliamuravev.ledger.support.LedgerApi;
import dev.iliamuravev.ledger.support.LedgerQueries;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

class TransferInvariantsIT extends IntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Autowired
    LedgerQueries queries;

    LedgerApi api;

    @BeforeEach
    void setUp() {
        api = new LedgerApi(http);
    }

    @Nested
    class EveryTransferCreatesTwoEntriesThatSumToZero {

        @Test
        void debitOnTheSourceAndCreditOnTheDestination() {
            var from = api.fundedAccount("EUR", 10_000);
            var to = api.openAccountId("EUR");

            var response = api.transfer(key(), from, to, 2_500, "EUR");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            var transferId = UUID.fromString(response.getBody().get("id").asText());
            assertThat(queries.entryAmountsOfTransfer(transferId)).containsExactly(-2_500L, 2_500L);
            assertThat(api.balance(from)).isEqualTo(7_500);
            assertThat(api.balance(to)).isEqualTo(2_500);
        }

        @Test
        void rejectedTransfersCreateNoEntries() {
            var from = api.openAccountId("EUR");
            var to = api.openAccountId("EUR");

            var response = api.transfer(key(), from, to, 1, "EUR");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            var transferId = UUID.fromString(response.getBody().get("transferId").asText());
            assertThat(queries.entryAmountsOfTransfer(transferId)).isEmpty();
        }
    }

    @Nested
    class SumOfAllEntriesIsZeroPerCurrency {

        @Test
        void afterDepositsTransfersAndRejections() {
            var a = api.fundedAccount("EUR", 5_000);
            var b = api.fundedAccount("USD", 7_000);
            var c = api.openAccountId("EUR");
            api.transfer(key(), a, c, 1_234, "EUR");
            api.transfer(key(), c, a, 99_999, "EUR");
            api.transfer(key(), a, b, 1, "EUR");

            assertThat(queries.sumOfAllEntriesByCurrency())
                    .isNotEmpty()
                    .allSatisfy((currency, sum) -> assertThat(sum).isEqualByComparingTo(BigDecimal.ZERO));
        }

        @Test
        void cachedBalancesMatchTheirEntries() {
            var a = api.fundedAccount("EUR", 5_000);
            var b = api.openAccountId("EUR");
            api.transfer(key(), a, b, 1_000, "EUR");
            api.transfer(key(), b, a, 400, "EUR");

            assertThat(queries.cachedBalance(a)).isEqualTo(queries.sumOfEntries(a)).isEqualTo(4_400);
            assertThat(queries.cachedBalance(b)).isEqualTo(queries.sumOfEntries(b)).isEqualTo(600);
        }
    }

    @Nested
    class BalanceNeverGoesBelowZero {

        @Test
        void insufficientFundsIsRejectedAndRecorded() {
            var from = api.fundedAccount("EUR", 1_000);
            var to = api.openAccountId("EUR");

            var response = api.transfer(key(), from, to, 1_001, "EUR");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().get("type").asText()).isEqualTo("/problems/insufficient-funds");
            assertThat(response.getBody().get("reason").asText()).isEqualTo("INSUFFICIENT_FUNDS");
            assertThat(api.balance(from)).isEqualTo(1_000);
            assertThat(api.balance(to)).isZero();

            var transfer = api.getTransfer(response.getBody().get("transferId").asText()).getBody();
            assertThat(transfer.get("status").asText()).isEqualTo("REJECTED");
            assertThat(transfer.get("reason").asText()).isEqualTo("INSUFFICIENT_FUNDS");
        }

        @Test
        void exactBalanceCanBeSpent() {
            var from = api.fundedAccount("EUR", 1_000);
            var to = api.openAccountId("EUR");

            assertThat(api.transfer(key(), from, to, 1_000, "EUR").getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(api.balance(from)).isZero();
        }
    }

    @Nested
    class TransfersOnlyBetweenActiveAccountsInTheSameCurrency {

        @Test
        void closedSourceAccount() {
            var from = api.openAccountId("EUR");
            var to = api.fundedAccount("EUR", 100);
            api.closeAccount(from);

            var response = api.transfer(key(), from, to, 1, "EUR");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().get("reason").asText()).isEqualTo("ACCOUNT_CLOSED");
        }

        @Test
        void closedDestinationAccount() {
            var from = api.fundedAccount("EUR", 100);
            var to = api.openAccountId("EUR");
            api.closeAccount(to);

            var response = api.transfer(key(), from, to, 1, "EUR");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().get("type").asText()).isEqualTo("/problems/account-closed");
            assertThat(api.balance(from)).isEqualTo(100);
        }

        @Test
        void accountsInDifferentCurrencies() {
            var eur = api.fundedAccount("EUR", 100);
            var usd = api.openAccountId("USD");

            var response = api.transfer(key(), eur, usd, 1, "EUR");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().get("reason").asText()).isEqualTo("CURRENCY_MISMATCH");
        }

        @Test
        void amountInAnotherCurrencyThanTheAccounts() {
            var a = api.fundedAccount("EUR", 100);
            var b = api.openAccountId("EUR");

            var response = api.transfer(key(), a, b, 1, "USD");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().get("reason").asText()).isEqualTo("CURRENCY_MISMATCH");
        }

        @Test
        void amountMustBePositive() {
            var a = api.fundedAccount("EUR", 100);
            var b = api.openAccountId("EUR");

            assertThat(api.transfer(key(), a, b, 0, "EUR").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(api.transfer(key(), a, b, -5, "EUR").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(api.balance(a)).isEqualTo(100);
        }

        @Test
        void sourceAndDestinationMustDiffer() {
            var a = api.fundedAccount("EUR", 100);

            var response = api.transfer(key(), a, a, 1, "EUR");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("detail").asText()).contains("same account");
        }

        @Test
        void settlementAccountsCannotBeUsedForCustomerTransfers() {
            var customer = api.openAccountId("EUR");
            var deposit = api.deposit(key(), customer, 500, "EUR").getBody();
            var settlement = UUID.fromString(deposit.get("fromAccountId").asText());

            var response = api.transfer(key(), settlement, customer, 1_000_000, "EUR");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().get("reason").asText()).isEqualTo("SETTLEMENT_ACCOUNT_NOT_ALLOWED");
            assertThat(api.balance(customer)).isEqualTo(500);
        }
    }

    @Test
    void unknownAccountIs404AndRecordsNothing() {
        var from = api.fundedAccount("EUR", 100);
        var missing = UUID.randomUUID();
        var key = key();

        var response = api.transfer(key, from, missing, 1, "EUR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("accountId").asText()).isEqualTo(missing.toString());
        assertThat(queries.transfersWithKey(key)).isZero();
    }

    @Test
    void unknownTransferIs404() {
        var response = api.getTransfer(UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("type").asText()).isEqualTo("/problems/transfer-not-found");
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }
}
