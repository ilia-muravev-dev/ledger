package dev.iliamuravev.ledger.statement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.iliamuravev.ledger.events.MalformedEventException;
import dev.iliamuravev.ledger.events.TransferRecordedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ledger.statement.projection", name = "enabled", havingValue = "true",
        matchIfMissing = true)
class StatementListener {

    private final StatementProjection projection;
    private final ObjectMapper json;

    StatementListener(StatementProjection projection, ObjectMapper json) {
        this.projection = projection;
        this.json = json;
    }

    @KafkaListener(id = StatementProjection.CONSUMER, groupId = StatementProjection.CONSUMER,
            topics = "${ledger.events.topic:transfer-events}")
    void onTransferRecorded(String payload) {
        projection.apply(parse(payload));
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
