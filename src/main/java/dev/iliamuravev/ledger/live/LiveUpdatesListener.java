package dev.iliamuravev.ledger.live;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.iliamuravev.ledger.events.TransferRecordedEvent;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ledger.live", name = "enabled", havingValue = "true", matchIfMissing = true)
class LiveUpdatesListener {

    private static final Logger log = LoggerFactory.getLogger(LiveUpdatesListener.class);

    private final LiveUpdatesHandler handler;
    private final ObjectMapper json;

    LiveUpdatesListener(LiveUpdatesHandler handler, ObjectMapper json) {
        this.handler = handler;
        this.json = json;
    }

    @KafkaListener(id = "live-updates", groupId = "ledger-live-${random.uuid}",
            topics = "${ledger.events.topic:transfer-events}", batch = "true",
            properties = "auto.offset.reset=latest")
    void onTransferRecorded(List<String> payloads) {
        var updates = new ArrayList<LiveUpdate>();
        for (var payload : payloads) {
            try {
                var event = json.readValue(payload, TransferRecordedEvent.class);
                var transfer = event.transfer();
                if (transfer == null) {
                    continue;
                }
                event.entries().forEach(entry -> updates.add(new LiveUpdate(
                        LiveUpdate.ENTRY_POSTED,
                        entry.accountId(),
                        entry.entryId(),
                        transfer.id(),
                        transfer.type(),
                        entry.counterpartyAccountId(),
                        entry.amount(),
                        entry.currency(),
                        entry.balanceAfter(),
                        transfer.createdAt())));
            } catch (JsonProcessingException e) {
                log.debug("Skipping a message that is not a transfer event: {}", e.getOriginalMessage());
            }
        }
        handler.broadcast(updates);
    }
}
