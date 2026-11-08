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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
class StatementRepository {

    private final DSLContext db;

    StatementRepository(DSLContext db) {
        this.db = db;
    }

    Set<UUID> markProcessed(String consumer, Collection<UUID> eventIds, Instant now) {
        if (eventIds.isEmpty()) {
            return Set.of();
        }
        var at = now.atOffset(ZoneOffset.UTC);
        var insert = db.insertInto(PROCESSED_EVENT, PROCESSED_EVENT.CONSUMER, PROCESSED_EVENT.EVENT_ID,
                PROCESSED_EVENT.PROCESSED_AT);
        for (var eventId : eventIds) {
            insert = insert.values(consumer, eventId, at);
        }
        return new HashSet<>(insert.onConflictDoNothing()
                .returningResult(PROCESSED_EVENT.EVENT_ID)
                .fetch(PROCESSED_EVENT.EVENT_ID));
    }

    List<NewLine> insertLines(List<NewLine> lines) {
        if (lines.isEmpty()) {
            return List.of();
        }
        var insert = db.insertInto(STATEMENT_ENTRY,
                STATEMENT_ENTRY.ENTRY_ID,
                STATEMENT_ENTRY.ACCOUNT_ID,
                STATEMENT_ENTRY.TRANSFER_ID,
                STATEMENT_ENTRY.TRANSFER_TYPE,
                STATEMENT_ENTRY.COUNTERPARTY_ACCOUNT_ID,
                STATEMENT_ENTRY.AMOUNT,
                STATEMENT_ENTRY.CURRENCY,
                STATEMENT_ENTRY.BALANCE_AFTER,
                STATEMENT_ENTRY.POSTED_AT);
        for (var line : lines) {
            var entry = line.entry();
            insert = insert.values(entry.entryId(), entry.accountId(), line.transferId(), line.transferType(),
                    entry.counterpartyAccountId(), entry.amount(), entry.currency(), entry.balanceAfter(),
                    line.postedAt().atOffset(ZoneOffset.UTC));
        }
        var inserted = new HashSet<>(insert.onConflictDoNothing()
                .returningResult(STATEMENT_ENTRY.ENTRY_ID)
                .fetch(STATEMENT_ENTRY.ENTRY_ID));
        return lines.stream().filter(line -> inserted.contains(line.entry().entryId())).toList();
    }

    void applyToBalances(List<BalanceDelta> deltas, Instant now) {
        if (deltas.isEmpty()) {
            return;
        }
        var view = ACCOUNT_BALANCE_VIEW;
        var at = now.atOffset(ZoneOffset.UTC);
        var insert = db.insertInto(view, view.ACCOUNT_ID, view.CURRENCY, view.BALANCE, view.LAST_ENTRY_ID,
                view.ENTRY_COUNT, view.UPDATED_AT);
        for (var delta : deltas.stream().sorted(Comparator.comparing(d -> d.accountId().toString())).toList()) {
            insert = insert.values(delta.accountId(), delta.currency(), delta.latestBalance(),
                    delta.latestEntryId(), delta.newEntries(), at);
        }
        var newer = excluded(view.LAST_ENTRY_ID).gt(view.LAST_ENTRY_ID);
        insert.onConflict(view.ACCOUNT_ID)
                .doUpdate()
                .set(view.BALANCE, when(newer, excluded(view.BALANCE)).otherwise(view.BALANCE))
                .set(view.LAST_ENTRY_ID, greatest(view.LAST_ENTRY_ID, excluded(view.LAST_ENTRY_ID)))
                .set(view.ENTRY_COUNT, view.ENTRY_COUNT.plus(excluded(view.ENTRY_COUNT)))
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

    record NewLine(EntryData entry, UUID transferId, String transferType, Instant postedAt) {
    }

    record BalanceDelta(UUID accountId, String currency, long latestEntryId, long latestBalance, long newEntries) {
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
