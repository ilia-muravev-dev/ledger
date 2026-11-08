package dev.iliamuravev.ledger.support;

import static dev.iliamuravev.ledger.jooq.Tables.ACCOUNT;
import static dev.iliamuravev.ledger.jooq.Tables.LEDGER_ENTRY;
import static dev.iliamuravev.ledger.jooq.Tables.TRANSFER;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.sum;
import static org.jooq.impl.DSL.val;

import dev.iliamuravev.ledger.domain.AccountType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

@Component
public class LedgerQueries {

    private final DSLContext db;

    public LedgerQueries(DSLContext db) {
        this.db = db;
    }

    public List<Long> entryAmountsOfTransfer(UUID transferId) {
        return db.select(LEDGER_ENTRY.AMOUNT)
                .from(LEDGER_ENTRY)
                .where(LEDGER_ENTRY.TRANSFER_ID.eq(transferId))
                .orderBy(LEDGER_ENTRY.ID)
                .fetch(LEDGER_ENTRY.AMOUNT);
    }

    public long sumOfEntries(UUID accountId) {
        return db.select(coalesce(sum(LEDGER_ENTRY.AMOUNT), val(BigDecimal.ZERO)))
                .from(LEDGER_ENTRY)
                .where(LEDGER_ENTRY.ACCOUNT_ID.eq(accountId))
                .fetchSingle()
                .value1()
                .longValueExact();
    }

    public Map<String, BigDecimal> sumOfAllEntriesByCurrency() {
        return db.select(LEDGER_ENTRY.CURRENCY, sum(LEDGER_ENTRY.AMOUNT))
                .from(LEDGER_ENTRY)
                .groupBy(LEDGER_ENTRY.CURRENCY)
                .fetchMap(LEDGER_ENTRY.CURRENCY, sum(LEDGER_ENTRY.AMOUNT));
    }

    public List<String> entriesOfTransfer(UUID transferId) {
        return db.select(LEDGER_ENTRY.ACCOUNT_ID, LEDGER_ENTRY.AMOUNT, LEDGER_ENTRY.CURRENCY)
                .from(LEDGER_ENTRY)
                .where(LEDGER_ENTRY.TRANSFER_ID.eq(transferId))
                .orderBy(LEDGER_ENTRY.ID)
                .fetch(r -> r.value1() + " " + r.value2() + " " + r.value3());
    }

    public UUID fxPositionAccount(String currency) {
        return db.select(ACCOUNT.ID).from(ACCOUNT)
                .where(ACCOUNT.CURRENCY.eq(currency))
                .and(ACCOUNT.TYPE.eq(AccountType.FX_POSITION))
                .fetchSingle(ACCOUNT.ID);
    }

    public long cachedBalance(UUID accountId) {
        return db.select(ACCOUNT.BALANCE).from(ACCOUNT).where(ACCOUNT.ID.eq(accountId)).fetchSingle().value1();
    }

    public int transfersWithKey(String idempotencyKey) {
        return db.select(count()).from(TRANSFER).where(TRANSFER.IDEMPOTENCY_KEY.eq(idempotencyKey))
                .fetchSingle().value1();
    }

    public int entryCount(UUID accountId) {
        return db.select(count()).from(LEDGER_ENTRY).where(LEDGER_ENTRY.ACCOUNT_ID.eq(accountId))
                .fetchSingle().value1();
    }
}
