package dev.iliamuravev.ledger.statement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.iliamuravev.ledger.config.LedgerClock;
import dev.iliamuravev.ledger.events.MalformedEventException;
import dev.iliamuravev.ledger.events.TransferRecordedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ledger.statement.projection", name = "enabled", havingValue = "true",
        matchIfMissing = true)
class StatementListener {

    private final StatementProjection projection;
    private final ObjectMapper json;
    private final LedgerClock clock;
    private final Timer lag;
    private final Counter applied;
    private final Counter duplicates;

    StatementListener(StatementProjection projection, ObjectMapper json, LedgerClock clock, MeterRegistry meters) {
        this.projection = projection;
        this.json = json;
        this.clock = clock;
        this.lag = Timer.builder("ledger.statement.projection.lag")
                .description("Time from the transfer commit to its projection into the statement")
                .publishPercentileHistogram()
                .register(meters);
        this.applied = Counter.builder("ledger.statement.events").tag("result", "applied").register(meters);
        this.duplicates = Counter.builder("ledger.statement.events").tag("result", "duplicate").register(meters);
    }

    @KafkaListener(id = StatementProjection.CONSUMER, groupId = StatementProjection.CONSUMER,
            topics = "${ledger.events.topic:transfer-events}", batch = "true")
    void onTransferRecorded(List<String> payloads) {
        var events = new ArrayList<TransferRecordedEvent>(payloads.size());
        MalformedEventException malformed = null;
        for (var payload : payloads) {
            try {
                events.add(parse(payload));
            } catch (MalformedEventException e) {
                malformed = e;
                break;
            }
        }
        if (!events.isEmpty()) {
            var results = projection.applyAll(events);
            var now = clock.now();
            for (int i = 0; i < events.size(); i++) {
                if (results.get(i)) {
                    applied.increment();
                    lag.record(Duration.between(events.get(i).occurredAt(), now));
                } else {
                    duplicates.increment();
                }
            }
        }
        if (malformed != null) {
            throw new BatchListenerFailedException(malformed.getMessage(), malformed, events.size());
        }
    }

    private TransferRecordedEvent parse(String payload) {
        try {
            var event = json.readValue(payload, TransferRecordedEvent.class);
            if (event.eventId() == null || event.transfer() == null) {
                throw new MalformedEventException("Event is missing its id or transfer", null);
            }
            return event;
        } catch (JsonProcessingException e) {
            throw new MalformedEventException("Event is not valid JSON", e);
        }
    }
}
