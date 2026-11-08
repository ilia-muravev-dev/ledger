package dev.iliamuravev.ledger.ledger;

import static dev.iliamuravev.ledger.jooq.Tables.LEDGER_ENTRY;

import dev.iliamuravev.ledger.domain.Transfer;
import java.time.ZoneOffset;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class LedgerEntryRepository {

    private final DSLContext db;

    public LedgerEntryRepository(DSLContext db) {
        this.db = db;
    }

    public void insertDoubleEntry(Transfer transfer) {
        if (!transfer.isCompleted()) {
            throw new IllegalArgumentException("Only completed transfers produce ledger entries");
        }
        var amount = transfer.amount();
        var createdAt = transfer.createdAt().atOffset(ZoneOffset.UTC);
        db.insertInto(LEDGER_ENTRY,
                        LEDGER_ENTRY.TRANSFER_ID,
                        LEDGER_ENTRY.ACCOUNT_ID,
                        LEDGER_ENTRY.AMOUNT,
                        LEDGER_ENTRY.CURRENCY,
                        LEDGER_ENTRY.CREATED_AT)
                .values(transfer.id(), transfer.fromAccountId(), amount.negate().amount(), amount.currencyCode(),
                        createdAt)
                .values(transfer.id(), transfer.toAccountId(), amount.amount(), amount.currencyCode(), createdAt)
                .execute();
    }
}
