package dev.iliamuravev.ledger.statement;

import static dev.iliamuravev.ledger.jooq.Tables.ACCOUNT_BALANCE_VIEW;
import static dev.iliamuravev.ledger.jooq.Tables.OUTBOX_EVENT;
import static dev.iliamuravev.ledger.jooq.Tables.PROCESSED_EVENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import dev.iliamuravev.ledger.events.TransferRecordedEvent;
import dev.iliamuravev.ledger.events.TransferRecordedEvent.EntryData;
import dev.iliamuravev.ledger.events.TransferRecordedEvent.TransferData;
import dev.iliamuravev.ledger.support.IntegrationTest;
import dev.iliamuravev.ledger.support.LedgerApi;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;

class StatementProjectionIT extends IntegrationTest {

    static final Duration EVENTUALLY = Duration.ofSeconds(20);

    @Autowired
    TestRestTemplate http;

    @Autowired
    DSLContext db;

    @Autowired
    StatementProjection projection;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    ConsumerFactory<String, String> consumers;

    LedgerApi api;

    @BeforeEach
    void setUp() {
        api = new LedgerApi(http);
    }

    @Test
    void statementCatchesUpWithTheLedger() {
        var alice = api.fundedAccount("EUR", 1_000);
        var bob = api.openAccountId("EUR");
        var transferId = api.transfer(UUID.randomUUID().toString(), alice, bob, 250, "EUR").getBody().get("id");
        api.transfer(UUID.randomUUID().toString(), bob, alice, 5_000, "EUR");

        var statement = await().atMost(EVENTUALLY).until(() -> statement(alice),
                s -> s.get("items").size() == 2);

        assertThat(statement.get("balance").asLong()).isEqualTo(api.balance(alice)).isEqualTo(750);
        var latest = statement.get("items").get(0);
        assertThat(latest.get("transferId")).isEqualTo(transferId);
        assertThat(latest.get("transferType").asText()).isEqualTo("TRANSFER");
        assertThat(latest.get("counterpartyAccountId").asText()).isEqualTo(bob.toString());
        assertThat(latest.get("amount").asLong()).isEqualTo(-250);
        assertThat(latest.get("balanceAfter").asLong()).isEqualTo(750);
        assertThat(statement.get("items").get(1).get("transferType").asText()).isEqualTo("DEPOSIT");
        assertThat(statement.get("items").get(1).get("balanceAfter").asLong()).isEqualTo(1_000);
    }

    @Test
    void republishingAfterARelayCrashDoesNotChangeTheReadModel() {
        var account = api.openAccountId("EUR");
        var transferId = UUID.fromString(
                api.deposit(UUID.randomUUID().toString(), account, 42, "EUR").getBody().get("id").asText());
        await().atMost(EVENTUALLY).until(() -> statement(account).get("items").size() == 1);
        var processedBefore = db.fetchCount(PROCESSED_EVENT);

        db.update(OUTBOX_EVENT).setNull(OUTBOX_EVENT.PUBLISHED_AT)
                .where(OUTBOX_EVENT.AGGREGATE_ID.eq(transferId)).execute();
        await().atMost(EVENTUALLY).until(() -> db.fetchExists(OUTBOX_EVENT,
                OUTBOX_EVENT.AGGREGATE_ID.eq(transferId).and(OUTBOX_EVENT.PUBLISHED_AT.isNotNull())));
        await().during(Duration.ofSeconds(2)).atMost(EVENTUALLY).until(() -> true);

        var statement = statement(account);
        assertThat(statement.get("items")).hasSize(1);
        assertThat(statement.get("balance").asLong()).isEqualTo(42);
        assertThat(entryCount(account)).isOne();
        assertThat(db.fetchCount(PROCESSED_EVENT)).isEqualTo(processedBefore);
    }

    @Test
    void eventsAppliedOutOfOrderAndTwiceConvergeToTheLatestBalance() {
        var account = UUID.randomUUID();
        var counterparty = UUID.randomUUID();
        var base = ThreadLocalRandom.current().nextLong(1L << 40, 1L << 50);
        var first = event(account, counterparty, base, 100, 100);
        var second = event(account, counterparty, base + 1, -60, 40);

        assertThat(projection.apply(second)).isTrue();
        assertThat(projection.apply(first)).isTrue();
        assertThat(projection.apply(second)).isFalse();

        var view = db.selectFrom(ACCOUNT_BALANCE_VIEW).where(ACCOUNT_BALANCE_VIEW.ACCOUNT_ID.eq(account)).fetchSingle();
        assertThat(view.getBalance()).isEqualTo(40);
        assertThat(view.getLastEntryId()).isEqualTo(base + 1);
        assertThat(view.getEntryCount()).isEqualTo(2);
    }

    @Test
    void batchWithDuplicatesAndSeveralEntriesPerAccountCountsEachEntryOnce() {
        var account = UUID.randomUUID();
        var counterparty = UUID.randomUUID();
        var base = ThreadLocalRandom.current().nextLong(1L << 40, 1L << 50);
        var a = event(account, counterparty, base, 100, 100);
        var b = event(account, counterparty, base + 1, 50, 150);
        var c = event(account, counterparty, base + 2, -20, 130);

        assertThat(projection.applyAll(List.of(c, a, a, b))).containsExactly(true, true, false, true);
        assertThat(projection.applyAll(List.of(a, b, c))).containsExactly(false, false, false);

        var view = db.selectFrom(ACCOUNT_BALANCE_VIEW).where(ACCOUNT_BALANCE_VIEW.ACCOUNT_ID.eq(account)).fetchSingle();
        assertThat(view.getBalance()).isEqualTo(130);
        assertThat(view.getEntryCount()).isEqualTo(3);
        assertThat(view.getLastEntryId()).isEqualTo(base + 2);
    }

    @Test
    void malformedMessagesGoToTheDeadLetterTopic() {
        var marker = "not-json-" + UUID.randomUUID();
        kafka.send("transfer-events", marker, marker);

        var props = Map.<String, Object>of(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = consumers.createConsumer("dlt-test-" + UUID.randomUUID(), null, null,
                toProperties(props))) {
            consumer.subscribe(List.of("transfer-events.DLT"));
            await().atMost(EVENTUALLY).untilAsserted(() -> assertThat(
                    KafkaTestUtils.getRecords(consumer, Duration.ofMillis(500)).records("transfer-events.DLT"))
                    .anySatisfy(record -> assertThat(record.value()).isEqualTo(marker)));
        }
        var account = api.openAccountId("EUR");
        api.fund(account, 7, "EUR");
        await().atMost(EVENTUALLY).until(() -> statement(account).get("items").size() == 1);
    }

    @Test
    void unknownAccountIs404AndEmptyAccountHasAnEmptyStatement() {
        assertThat(api.get("/accounts/{id}/statement", UUID.randomUUID()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        var empty = statement(api.openAccountId("EUR"));
        assertThat(empty.get("items")).isEmpty();
        assertThat(empty.get("balance").asLong()).isZero();
    }

    private JsonNode statement(UUID account) {
        var response = api.get("/accounts/{id}/statement", account);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private long entryCount(UUID account) {
        return db.select(ACCOUNT_BALANCE_VIEW.ENTRY_COUNT).from(ACCOUNT_BALANCE_VIEW)
                .where(ACCOUNT_BALANCE_VIEW.ACCOUNT_ID.eq(account)).fetchSingle().value1();
    }

    private static TransferRecordedEvent event(UUID account, UUID counterparty, long entryId, long amount,
            long balanceAfter) {
        var transfer = new TransferData(UUID.randomUUID(), "TRANSFER", counterparty, account, Math.abs(amount), "EUR",
                "COMPLETED", null, null, null, null, null, Instant.now());
        return new TransferRecordedEvent(UUID.randomUUID(), Instant.now(), transfer,
                List.of(new EntryData(entryId, account, counterparty, amount, "EUR", balanceAfter)));
    }

    private static Properties toProperties(Map<String, Object> map) {
        var properties = new Properties();
        properties.putAll(map);
        return properties;
    }
}
