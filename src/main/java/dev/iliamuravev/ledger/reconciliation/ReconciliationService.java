package dev.iliamuravev.ledger.reconciliation;

import static dev.iliamuravev.ledger.jooq.Tables.ACCOUNT;
import static dev.iliamuravev.ledger.jooq.Tables.LEDGER_ENTRY;
import static dev.iliamuravev.ledger.jooq.Tables.TRANSFER;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.sum;
import static org.jooq.impl.DSL.val;
import static org.jooq.impl.DSL.when;

import dev.iliamuravev.ledger.config.LedgerClock;
import dev.iliamuravev.ledger.domain.TransferStatus;
import dev.iliamuravev.ledger.reconciliation.ReconciliationReport.BalanceMismatch;
import dev.iliamuravev.ledger.reconciliation.ReconciliationReport.MalformedTransfer;
import dev.iliamuravev.ledger.reconciliation.ReconciliationReport.UnbalancedCurrency;
import java.math.BigDecimal;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationService {

    private final DSLContext db;
    private final LedgerClock clock;

    public ReconciliationService(DSLContext db, LedgerClock clock) {
        this.db = db;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReconciliationReport run() {
        var entrySum = coalesce(sum(LEDGER_ENTRY.AMOUNT), val(BigDecimal.ZERO));
        var entryCount = count(LEDGER_ENTRY.ID);

        var balanceMismatches = db.select(ACCOUNT.ID, ACCOUNT.BALANCE, entrySum)
                .from(ACCOUNT)
                .leftJoin(LEDGER_ENTRY).on(LEDGER_ENTRY.ACCOUNT_ID.eq(ACCOUNT.ID))
                .groupBy(ACCOUNT.ID, ACCOUNT.BALANCE)
                .having(entrySum.ne(ACCOUNT.BALANCE.cast(BigDecimal.class)))
                .orderBy(ACCOUNT.ID)
                .fetch(r -> new BalanceMismatch(r.value1(), r.value2(), r.value3()));

        var unbalancedCurrencies = db.select(LEDGER_ENTRY.CURRENCY, sum(LEDGER_ENTRY.AMOUNT))
                .from(LEDGER_ENTRY)
                .groupBy(LEDGER_ENTRY.CURRENCY)
                .having(sum(LEDGER_ENTRY.AMOUNT).ne(BigDecimal.ZERO))
                .orderBy(LEDGER_ENTRY.CURRENCY)
                .fetch(r -> new UnbalancedCurrency(r.value1(), r.value2()));

        var expectedEntries = when(TRANSFER.TARGET_CURRENCY.isNull(), inline(2)).otherwise(inline(4));
        var unbalancedTransfers = select(LEDGER_ENTRY.TRANSFER_ID)
                .from(LEDGER_ENTRY)
                .groupBy(LEDGER_ENTRY.TRANSFER_ID, LEDGER_ENTRY.CURRENCY)
                .having(sum(LEDGER_ENTRY.AMOUNT).ne(BigDecimal.ZERO));
        var completedButMalformed = TRANSFER.STATUS.eq(TransferStatus.COMPLETED)
                .and(entryCount.ne(expectedEntries));
        var rejectedWithEntries = TRANSFER.STATUS.eq(TransferStatus.REJECTED).and(entryCount.ne(0));
        var malformedTransfers = db.select(TRANSFER.ID, TRANSFER.STATUS, entryCount, entrySum)
                .from(TRANSFER)
                .leftJoin(LEDGER_ENTRY).on(LEDGER_ENTRY.TRANSFER_ID.eq(TRANSFER.ID))
                .groupBy(TRANSFER.ID, TRANSFER.STATUS, TRANSFER.TARGET_CURRENCY)
                .having(completedButMalformed.or(rejectedWithEntries).or(TRANSFER.ID.in(unbalancedTransfers)))
                .orderBy(TRANSFER.ID)
                .fetch(r -> new MalformedTransfer(r.value1(), r.value2(), r.value3(), r.value4()));

        var accountsChecked = db.fetchCount(ACCOUNT);
        var transfersChecked = db.fetchCount(TRANSFER);
        var consistent = balanceMismatches.isEmpty() && unbalancedCurrencies.isEmpty()
                && malformedTransfers.isEmpty();

        return new ReconciliationReport(clock.now(), consistent, accountsChecked, transfersChecked,
                balanceMismatches, unbalancedCurrencies, malformedTransfers);
    }
}
