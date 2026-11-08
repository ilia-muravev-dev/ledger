package dev.iliamuravev.ledger.events;

import static dev.iliamuravev.ledger.jooq.Tables.OUTBOX_EVENT;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

@Repository
class OutboxRepository {

    private final DSLContext db;

    OutboxRepository(DSLContext db) {
        this.db = db;
    }

    void append(UUID id, String aggregateType, UUID aggregateId, String eventType, String payload, Instant now) {
        db.insertInto(OUTBOX_EVENT)
                .set(OUTBOX_EVENT.ID, id)
                .set(OUTBOX_EVENT.AGGREGATE_TYPE, aggregateType)
                .set(OUTBOX_EVENT.AGGREGATE_ID, aggregateId)
                .set(OUTBOX_EVENT.EVENT_TYPE, eventType)
                .set(OUTBOX_EVENT.PAYLOAD, JSONB.valueOf(payload))
                .set(OUTBOX_EVENT.CREATED_AT, now.atOffset(ZoneOffset.UTC))
                .execute();
    }

    List<PendingEvent> lockUnpublished(int limit) {
        return db.select(OUTBOX_EVENT.ID, OUTBOX_EVENT.AGGREGATE_ID, OUTBOX_EVENT.EVENT_TYPE, OUTBOX_EVENT.PAYLOAD)
                .from(OUTBOX_EVENT)
                .where(OUTBOX_EVENT.PUBLISHED_AT.isNull())
                .orderBy(OUTBOX_EVENT.SEQ)
                .limit(limit)
                .forUpdate()
                .skipLocked()
                .fetch(r -> new PendingEvent(r.value1(), r.value2(), r.value3(), r.value4().data()));
    }

    void markPublished(List<UUID> ids, Instant now) {
        db.update(OUTBOX_EVENT)
                .set(OUTBOX_EVENT.PUBLISHED_AT, now.atOffset(ZoneOffset.UTC))
                .where(OUTBOX_EVENT.ID.in(ids))
                .execute();
    }

    int countUnpublished() {
        return db.fetchCount(OUTBOX_EVENT, OUTBOX_EVENT.PUBLISHED_AT.isNull());
    }

    Optional<Instant> oldestUnpublishedAt() {
        return db.select(OUTBOX_EVENT.CREATED_AT)
                .from(OUTBOX_EVENT)
                .where(OUTBOX_EVENT.PUBLISHED_AT.isNull())
                .orderBy(OUTBOX_EVENT.SEQ)
                .limit(1)
                .fetchOptional(r -> r.value1().toInstant());
    }

    int deletePublishedBefore(Instant cutoff) {
        return db.deleteFrom(OUTBOX_EVENT)
                .where(OUTBOX_EVENT.PUBLISHED_AT.lt(cutoff.atOffset(ZoneOffset.UTC)))
                .execute();
    }

    record PendingEvent(UUID id, UUID aggregateId, String eventType, String payload) {
    }
}
