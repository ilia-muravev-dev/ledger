package dev.iliamuravev.ledger.persistence;

import static dev.iliamuravev.ledger.jooq.Tables.ACCOUNT;
import static dev.iliamuravev.ledger.jooq.Tables.TRANSFER;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.iliamuravev.ledger.domain.AccountStatus;
import dev.iliamuravev.ledger.domain.AccountType;
import dev.iliamuravev.ledger.domain.TransferStatus;
import dev.iliamuravev.ledger.domain.TransferType;
import dev.iliamuravev.ledger.support.IntegrationTest;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

class SchemaConstraintsIT extends IntegrationTest {

    @Autowired
    DSLContext db;

    @Autowired
    TransactionTemplate tx;

    @Test
    void customerBalanceCannotBeNegative() {
        assertThatThrownBy(() -> inRolledBackTx(() -> insertAccount(AccountType.CUSTOMER, "EUR", -1)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("account_balance_check");
    }

    @Test
    void settlementBalanceMayBeNegative() {
        assertThatCode(() -> inRolledBackTx(() -> insertAccount(AccountType.SETTLEMENT, "XTS", -1)))
                .doesNotThrowAnyException();
    }

    @Test
    void onlyOneSettlementAccountPerCurrency() {
        assertThatThrownBy(() -> inRolledBackTx(() -> {
            insertAccount(AccountType.SETTLEMENT, "XTS", 0);
            insertAccount(AccountType.SETTLEMENT, "XTS", 0);
        }))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("account_settlement_currency_uq");
    }

    @Test
    void transferAmountMustBePositive() {
        assertThatThrownBy(() -> inRolledBackTx(() -> {
            var a = insertAccount(AccountType.CUSTOMER, "EUR", 0);
            var b = insertAccount(AccountType.CUSTOMER, "EUR", 0);
            insertTransfer(a, b, 0);
        }))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("transfer_amount_check");
    }

    @Test
    void transferMustBeBetweenDistinctAccounts() {
        assertThatThrownBy(() -> inRolledBackTx(() -> {
            var a = insertAccount(AccountType.CUSTOMER, "EUR", 0);
            insertTransfer(a, a, 1);
        }))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("transfer_distinct_accounts_check");
    }

    private void inRolledBackTx(Runnable action) {
        tx.executeWithoutResult(status -> {
            status.setRollbackOnly();
            action.run();
        });
    }

    private UUID insertAccount(AccountType type, String currency, long balance) {
        var id = UUID.randomUUID();
        db.insertInto(ACCOUNT)
                .set(ACCOUNT.ID, id)
                .set(ACCOUNT.OWNER, "schema-test")
                .set(ACCOUNT.CURRENCY, currency)
                .set(ACCOUNT.TYPE, type)
                .set(ACCOUNT.STATUS, AccountStatus.ACTIVE)
                .set(ACCOUNT.BALANCE, balance)
                .set(ACCOUNT.CREATED_AT, OffsetDateTime.now())
                .execute();
        return id;
    }

    private void insertTransfer(UUID from, UUID to, long amount) {
        db.insertInto(TRANSFER)
                .set(TRANSFER.ID, UUID.randomUUID())
                .set(TRANSFER.IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                .set(TRANSFER.TYPE, TransferType.TRANSFER)
                .set(TRANSFER.FROM_ACCOUNT_ID, from)
                .set(TRANSFER.TO_ACCOUNT_ID, to)
                .set(TRANSFER.AMOUNT, amount)
                .set(TRANSFER.CURRENCY, "EUR")
                .set(TRANSFER.STATUS, TransferStatus.COMPLETED)
                .set(TRANSFER.CREATED_AT, OffsetDateTime.now())
                .execute();
    }
}
