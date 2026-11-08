package dev.iliamuravev.ledger.events;

import static dev.iliamuravev.ledger.jooq.Tables.LEDGER_ENTRY;
import static dev.iliamuravev.ledger.jooq.Tables.OUTBOX_EVENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.iliamuravev.ledger.support.IntegrationTest;
import dev.iliamuravev.ledger.support.LedgerApi;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

class OutboxIT extends IntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Autowired
    DSLContext db;

    @Autowired
    ObjectMapper json;

    LedgerApi api;

    @BeforeEach
    void setUp() {
        api = new LedgerApi(http);
    }

    @Test
    void completedTransferWritesOneEventWithBothEntriesAndResultingBalances() throws Exception {
        var from = api.fundedAccount("EUR", 1_000);
        var to = api.openAccountId("EUR");
        var transferId = UUID.fromString(
                api.transfer(UUID.randomUUID().toString(), from, to, 300, "EUR").getBody().get("id").asText());

        var events = eventsFor(transferId);

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.transfer().status()).isEqualTo("COMPLETED");
            assertThat(event.transfer().amount()).isEqualTo(300);
            assertThat(event.entries()).extracting(TransferRecordedEvent.EntryData::accountId,
                            TransferRecordedEvent.EntryData::counterpartyAccountId,
                            TransferRecordedEvent.EntryData::amount,
                            TransferRecordedEvent.EntryData::balanceAfter)
                    .containsExactly(
                            tuple(from, to, -300L, 700L),
                            tuple(to, from, 300L, 300L));
            assertThat(event.entries()).extracting(TransferRecordedEvent.EntryData::entryId)
                    .containsExactlyInAnyOrderElementsOf(entryIdsOf(transferId));
        });
    }

    @Test
    void rejectedTransferWritesAnEventWithoutEntries() throws Exception {
        var from = api.openAccountId("EUR");
        var to = api.openAccountId("EUR");
        var response = api.transfer(UUID.randomUUID().toString(), from, to, 1, "EUR");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        var events = eventsFor(UUID.fromString(response.getBody().get("transferId").asText()));

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.transfer().status()).isEqualTo("REJECTED");
            assertThat(event.transfer().reason()).isEqualTo("INSUFFICIENT_FUNDS");
            assertThat(event.entries()).isEmpty();
        });
    }

    @Test
    void replayedRequestDoesNotWriteASecondEvent() throws Exception {
        var from = api.fundedAccount("EUR", 1_000);
        var to = api.openAccountId("EUR");
        var key = UUID.randomUUID().toString();
        var transferId = UUID.fromString(api.transfer(key, from, to, 10, "EUR").getBody().get("id").asText());

        api.transfer(key, from, to, 10, "EUR");

        assertThat(eventsFor(transferId)).hasSize(1);
    }

    @Test
    void failedRequestWritesNothing() {
        var from = api.fundedAccount("EUR", 1_000);
        var before = db.fetchCount(OUTBOX_EVENT);

        api.transfer(UUID.randomUUID().toString(), from, UUID.randomUUID(), 10, "EUR");

        assertThat(db.fetchCount(OUTBOX_EVENT)).isEqualTo(before);
    }

    @Test
    void relayPublishesAndMarksEvents() {
        var account = api.openAccountId("EUR");
        var transferId = UUID.fromString(
                api.deposit(UUID.randomUUID().toString(), account, 5, "EUR").getBody().get("id").asText());

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(
                db.select(OUTBOX_EVENT.PUBLISHED_AT).from(OUTBOX_EVENT)
                        .where(OUTBOX_EVENT.AGGREGATE_ID.eq(transferId))
                        .fetchSingle().value1())
                .isNotNull());
    }

    private List<TransferRecordedEvent> eventsFor(UUID transferId) throws Exception {
        var payloads = db.select(OUTBOX_EVENT.PAYLOAD).from(OUTBOX_EVENT)
                .where(OUTBOX_EVENT.AGGREGATE_ID.eq(transferId))
                .fetch(r -> r.value1().data());
        var events = new ArrayList<TransferRecordedEvent>();
        for (var payload : payloads) {
            events.add(json.readValue(payload, TransferRecordedEvent.class));
        }
        return events;
    }

    private List<Long> entryIdsOf(UUID transferId) {
        return db.select(LEDGER_ENTRY.ID).from(LEDGER_ENTRY).where(LEDGER_ENTRY.TRANSFER_ID.eq(transferId))
                .fetch(LEDGER_ENTRY.ID);
    }
}
