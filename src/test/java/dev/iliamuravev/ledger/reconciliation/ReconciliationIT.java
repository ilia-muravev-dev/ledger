package dev.iliamuravev.ledger.reconciliation;

import static dev.iliamuravev.ledger.jooq.Tables.ACCOUNT;
import static dev.iliamuravev.ledger.jooq.Tables.LEDGER_ENTRY;
import static org.assertj.core.api.Assertions.assertThat;

import dev.iliamuravev.ledger.support.IntegrationTest;
import dev.iliamuravev.ledger.support.LedgerApi;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Consumer;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;

class ReconciliationIT extends IntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Autowired
    ReconciliationService reconciliation;

    @Autowired
    DSLContext db;

    @Autowired
    TransactionTemplate tx;

    LedgerApi api;

    @BeforeEach
    void setUp() {
        api = new LedgerApi(http);
    }

    @Test
    void ledgerBuiltThroughTheApiIsConsistent() {
        var a = api.fundedAccount("EUR", 1_000);
        var b = api.openAccountId("EUR");
        api.transfer(UUID.randomUUID().toString(), a, b, 400, "EUR");
        api.transfer(UUID.randomUUID().toString(), b, a, 999, "EUR");

        var report = reconciliation.run();

        assertThat(report.consistent()).isTrue();
        assertThat(report.accountsChecked()).isPositive();
        assertThat(report.transfersChecked()).isPositive();
    }

    @Test
    void detectsCachedBalanceThatDriftedFromEntries() {
        var account = api.fundedAccount("EUR", 1_000);

        var report = withCorruption(db -> db.update(ACCOUNT)
                .set(ACCOUNT.BALANCE, ACCOUNT.BALANCE.plus(1))
                .where(ACCOUNT.ID.eq(account))
                .execute());

        assertThat(report.consistent()).isFalse();
        assertThat(report.balanceMismatches()).singleElement().satisfies(mismatch -> {
            assertThat(mismatch.accountId()).isEqualTo(account);
            assertThat(mismatch.cachedBalance()).isEqualTo(1_001);
            assertThat(mismatch.sumOfEntries()).isEqualByComparingTo("1000");
        });
    }

    @Test
    void detectsOneSidedEntry() {
        var account = api.fundedAccount("EUR", 1_000);
        var transferId = UUID.fromString(
                api.transfer(UUID.randomUUID().toString(), account, api.openAccountId("EUR"), 10, "EUR")
                        .getBody().get("id").asText());

        var report = withCorruption(db -> db.insertInto(LEDGER_ENTRY)
                .set(LEDGER_ENTRY.TRANSFER_ID, transferId)
                .set(LEDGER_ENTRY.ACCOUNT_ID, account)
                .set(LEDGER_ENTRY.AMOUNT, 5L)
                .set(LEDGER_ENTRY.CURRENCY, "EUR")
                .set(LEDGER_ENTRY.CREATED_AT, OffsetDateTime.now())
                .execute());

        assertThat(report.consistent()).isFalse();
        assertThat(report.unbalancedCurrencies()).singleElement()
                .satisfies(c -> assertThat(c.currency()).isEqualTo("EUR"));
        assertThat(report.malformedTransfers()).singleElement()
                .satisfies(t -> assertThat(t.transferId()).isEqualTo(transferId));
        assertThat(report.balanceMismatches()).extracting(ReconciliationReport.BalanceMismatch::accountId)
                .containsExactly(account);
    }

    @Test
    void isExposedAsAnActuatorEndpoint() {
        var response = api.get("/actuator/reconciliation");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("consistent").asBoolean()).isTrue();
    }

    private ReconciliationReport withCorruption(Consumer<DSLContext> corruption) {
        return tx.execute(status -> {
            status.setRollbackOnly();
            corruption.accept(db);
            return reconciliation.run();
        });
    }
}
