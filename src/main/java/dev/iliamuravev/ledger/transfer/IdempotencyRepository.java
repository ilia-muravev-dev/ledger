package dev.iliamuravev.ledger.transfer;

import static dev.iliamuravev.ledger.jooq.Tables.IDEMPOTENCY_RECORD;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
class IdempotencyRepository {

    private final DSLContext db;

    IdempotencyRepository(DSLContext db) {
        this.db = db;
    }

    boolean claim(String key, String requestHash, Instant now) {
        return db.insertInto(IDEMPOTENCY_RECORD)
                .set(IDEMPOTENCY_RECORD.IDEMPOTENCY_KEY, key)
                .set(IDEMPOTENCY_RECORD.REQUEST_HASH, requestHash)
                .set(IDEMPOTENCY_RECORD.CREATED_AT, now.atOffset(ZoneOffset.UTC))
                .onConflictDoNothing()
                .execute() == 1;
    }

    Optional<StoredRequest> find(String key) {
        return db.select(IDEMPOTENCY_RECORD.REQUEST_HASH, IDEMPOTENCY_RECORD.TRANSFER_ID)
                .from(IDEMPOTENCY_RECORD)
                .where(IDEMPOTENCY_RECORD.IDEMPOTENCY_KEY.eq(key))
                .fetchOptional(r -> new StoredRequest(r.value1(), r.value2()));
    }

    void complete(String key, UUID transferId) {
        db.update(IDEMPOTENCY_RECORD)
                .set(IDEMPOTENCY_RECORD.TRANSFER_ID, transferId)
                .where(IDEMPOTENCY_RECORD.IDEMPOTENCY_KEY.eq(key))
                .execute();
    }

    record StoredRequest(String requestHash, UUID transferId) {
    }
}
