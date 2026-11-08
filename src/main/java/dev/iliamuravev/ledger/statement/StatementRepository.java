package dev.iliamuravev.ledger.statement;

import static dev.iliamuravev.ledger.jooq.Tables.ACCOUNT_BALANCE_VIEW;
import static dev.iliamuravev.ledger.jooq.Tables.PROCESSED_EVENT;
import static dev.iliamuravev.ledger.jooq.Tables.STATEMENT_ENTRY;
import static org.jooq.impl.DSL.excluded;
import static org.jooq.impl.DSL.greatest;
import static org.jooq.impl.DSL.when;

import dev.iliamuravev.ledger.events.TransferRecordedEvent.EntryData;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
class StatementRepository {

    private final DSLContext db;

    StatementRepository(DSLContext db) {
        this.db = db;
    }

    boolean markProcessed(String consumer, UUID eventId, Instant now) {
        return db.insertInto(PROCESSED_EVENT)
                .set(PROCESSED_EVENT.CONSUMER, consumer)
                .set(PROCESSED_EVENT.EVENT_ID, eventId)
                .set(PROCESSED_EVENT.PROCESSED_AT, now.atOffset(ZoneOffset.UTC))
                .onConflictDoNothing()
                .execute() == 1;
    }

    boolean insertEntry(EntryData entry, UUID transferId, String transferType, Instant postedAt) {
        return db.insertInto(STATEMENT_ENTRY)
                .set(STATEMENT_ENTRY.ENTRY_ID, entry.entryId())
                .set(STATEMENT_ENTRY.ACCOUNT_ID, entry.accountId())
                .set(STATEMENT_ENTRY.TRANSFER_ID, transferId)
                .set(STATEMENT_ENTRY.TRANSFER_TYPE, transferType)
                .set(STATEMENT_ENTRY.COUNTERPARTY_ACCOUNT_ID, entry.counterpartyAccountId())
                .set(STATEMENT_ENTRY.AMOUNT, entry.amount())
                .set(STATEMENT_ENTRY.CURRENCY, entry.currency())
                .set(STATEMENT_ENTRY.BALANCE_AFTER, entry.balanceAfter())
                .set(STATEMENT_ENTRY.POSTED_AT, postedAt.atOffset(ZoneOffset.UTC))
                .onConflictDoNothing()
                .execute() == 1;
    }

    void applyToBalance(EntryData entry, Instant now) {
        var view = ACCOUNT_BALANCE_VIEW;
        var newer = excluded(view.LAST_ENTRY_ID).gt(view.LAST_ENTRY_ID);
        db.insertInto(view)
                .set(view.ACCOUNT_ID, entry.accountId())
                .set(view.CURRENCY, entry.currency())
                .set(view.BALANCE, entry.balanceAfter())
                .set(view.LAST_ENTRY_ID, entry.entryId())
                .set(view.ENTRY_COUNT, 1L)
                .set(view.UPDATED_AT, now.atOffset(ZoneOffset.UTC))
                .onConflict(view.ACCOUNT_ID)
                .doUpdate()
                .set(view.BALANCE, when(newer, excluded(view.BALANCE)).otherwise(view.BALANCE))
                .set(view.LAST_ENTRY_ID, greatest(view.LAST_ENTRY_ID, excluded(view.LAST_ENTRY_ID)))
                .set(view.ENTRY_COUNT, view.ENTRY_COUNT.plus(1))
                .set(view.UPDATED_AT, excluded(view.UPDATED_AT))
                .execute();
    }

    Optional<BalanceView> findBalance(UUID accountId) {
        return db.select(ACCOUNT_BALANCE_VIEW.BALANCE, ACCOUNT_BALANCE_VIEW.LAST_ENTRY_ID,
                        ACCOUNT_BALANCE_VIEW.ENTRY_COUNT, ACCOUNT_BALANCE_VIEW.UPDATED_AT)
                .from(ACCOUNT_BALANCE_VIEW)
                .where(ACCOUNT_BALANCE_VIEW.ACCOUNT_ID.eq(accountId))
                .fetchOptional(r -> new BalanceView(r.value1(), r.value2(), r.value3(), r.value4().toInstant()));
    }

    List<StatementLine> findPage(UUID accountId, Optional<Long> beforeEntryId, int limit) {
        var condition = STATEMENT_ENTRY.ACCOUNT_ID.eq(accountId);
        if (beforeEntryId.isPresent()) {
            condition = condition.and(STATEMENT_ENTRY.ENTRY_ID.lt(beforeEntryId.get()));
        }
        return db.selectFrom(STATEMENT_ENTRY)
                .where(condition)
                .orderBy(STATEMENT_ENTRY.ENTRY_ID.desc())
                .limit(limit)
                .fetch(r -> new StatementLine(r.getEntryId(), r.getTransferId(), r.getTransferType(),
                        r.getCounterpartyAccountId(), r.getAmount(), r.getCurrency(), r.getBalanceAfter(),
                        r.getPostedAt().toInstant()));
    }

    record BalanceView(long balance, long lastEntryId, long entryCount, Instant updatedAt) {
    }

    record StatementLine(
            long entryId,
            UUID transferId,
            String transferType,
            UUID counterpartyAccountId,
            long amount,
            String currency,
            long balanceAfter,
            Instant postedAt) {
    }
}
