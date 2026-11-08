package dev.iliamuravev.ledger.ledger;

import static dev.iliamuravev.ledger.jooq.Tables.LEDGER_ENTRY;

import dev.iliamuravev.ledger.domain.LedgerEntry;
import dev.iliamuravev.ledger.domain.Money;
import dev.iliamuravev.ledger.domain.Posting;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class LedgerEntryRepository {

    private final DSLContext db;

    public LedgerEntryRepository(DSLContext db) {
        this.db = db;
    }

    public List<LedgerEntry> findPage(UUID accountId, Currency currency, Optional<Long> beforeEntryId, int limit) {
        var condition = LEDGER_ENTRY.ACCOUNT_ID.eq(accountId);
        if (beforeEntryId.isPresent()) {
            condition = condition.and(LEDGER_ENTRY.ID.lt(beforeEntryId.get()));
        }
        return db.selectFrom(LEDGER_ENTRY)
                .where(condition)
                .orderBy(LEDGER_ENTRY.ID.desc())
                .limit(limit)
                .fetch(r -> new LedgerEntry(r.getId(), r.getTransferId(), r.getAccountId(),
                        new Money(r.getAmount(), currency), r.getCreatedAt().toInstant()));
    }

    public Map<UUID, Long> insertPostings(UUID transferId, List<Posting> postings, Instant createdAt) {
        if (postings.isEmpty()) {
            return Map.of();
        }
        var at = createdAt.atOffset(ZoneOffset.UTC);
        var insert = db.insertInto(LEDGER_ENTRY,
                LEDGER_ENTRY.TRANSFER_ID,
                LEDGER_ENTRY.ACCOUNT_ID,
                LEDGER_ENTRY.AMOUNT,
                LEDGER_ENTRY.CURRENCY,
                LEDGER_ENTRY.CREATED_AT);
        for (var posting : postings) {
            insert = insert.values(transferId, posting.accountId(), posting.amount().amount(),
                    posting.amount().currencyCode(), at);
        }
        return insert.returningResult(LEDGER_ENTRY.ACCOUNT_ID, LEDGER_ENTRY.ID)
                .fetchMap(LEDGER_ENTRY.ACCOUNT_ID, LEDGER_ENTRY.ID);
    }
}
