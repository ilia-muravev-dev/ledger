package dev.iliamuravev.ledger.events;

import dev.iliamuravev.ledger.config.LedgerClock;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(prefix = "ledger.events.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    static final String EVENT_ID_HEADER = "eventId";
    static final String EVENT_TYPE_HEADER = "eventType";

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate tx;
    private final EventsProperties properties;
    private final LedgerClock clock;
    private final Counter publishedEvents;

    OutboxRelay(OutboxRepository outbox, KafkaTemplate<String, String> kafka, TransactionTemplate tx,
            EventsProperties properties, LedgerClock clock, MeterRegistry meters) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.tx = tx;
        this.properties = properties;
        this.clock = clock;
        this.publishedEvents = Counter.builder("ledger.outbox.published")
                .description("Outbox events published to Kafka")
                .register(meters);
    }

    @Scheduled(fixedDelayString = "${ledger.events.relay.poll-interval-ms:200}")
    void poll() {
        try {
            int published;
            do {
                published = publishBatch();
            } while (published == properties.relay().batchSize());
        } catch (RuntimeException e) {
            log.warn("Outbox relay could not publish, will retry: {}", e.getMessage());
        }
    }

    public int publishBatch() {
        var count = tx.execute(status -> {
            var pending = outbox.lockUnpublished(properties.relay().batchSize());
            if (pending.isEmpty()) {
                return 0;
            }
            var sends = new ArrayList<CompletableFuture<?>>(pending.size());
            for (var event : pending) {
                var record = new ProducerRecord<>(properties.topic(), event.aggregateId().toString(), event.payload());
                record.headers().add(EVENT_ID_HEADER, event.id().toString().getBytes(StandardCharsets.UTF_8));
                record.headers().add(EVENT_TYPE_HEADER, event.eventType().getBytes(StandardCharsets.UTF_8));
                sends.add(kafka.send(record));
            }
            awaitAll(sends);
            outbox.markPublished(pending.stream().map(OutboxRepository.PendingEvent::id).toList(), clock.now());
            publishedEvents.increment(pending.size());
            return pending.size();
        });
        return count == null ? 0 : count;
    }

    @Scheduled(cron = "${ledger.events.relay.cleanup-cron:0 17 3 * * *}")
    void deletePublished() {
        var removed = outbox.deletePublishedBefore(clock.now().minus(properties.relay().retention()));
        if (removed > 0) {
            log.info("Deleted {} published outbox events", removed);
        }
    }

    private void awaitAll(ArrayList<CompletableFuture<?>> sends) {
        try {
            CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new))
                    .get(properties.relay().sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing outbox events", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Publishing outbox events failed", e);
        }
    }
}
