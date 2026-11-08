package dev.iliamuravev.ledger.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.iliamuravev.ledger.config.LedgerClock;
import dev.iliamuravev.ledger.domain.Account;
import dev.iliamuravev.ledger.domain.Transfer;
import dev.iliamuravev.ledger.events.TransferRecordedEvent.EntryData;
import dev.iliamuravev.ledger.events.TransferRecordedEvent.TransferData;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransferEventWriter {

    static final String AGGREGATE_TYPE = "Transfer";

    private final OutboxRepository outbox;
    private final ObjectMapper json;
    private final LedgerClock clock;

    TransferEventWriter(OutboxRepository outbox, ObjectMapper json, LedgerClock clock) {
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void transferRecorded(Transfer transfer, Account from, Account to, Map<UUID, Long> entryIds) {
        var amount = transfer.amount().amount();
        var currency = transfer.amount().currencyCode();
        var entries = transfer.isCompleted()
                ? List.of(
                        new EntryData(entryIds.get(from.id()), from.id(), to.id(), -amount, currency,
                                Math.subtractExact(from.balance().amount(), amount)),
                        new EntryData(entryIds.get(to.id()), to.id(), from.id(), amount, currency,
                                Math.addExact(to.balance().amount(), amount)))
                : List.<EntryData>of();
        var event = new TransferRecordedEvent(UUID.randomUUID(), clock.now(), new TransferData(
                transfer.id(),
                transfer.type().name(),
                transfer.fromAccountId(),
                transfer.toAccountId(),
                amount,
                currency,
                transfer.status().name(),
                transfer.reason() == null ? null : transfer.reason().name(),
                transfer.createdAt()), entries);
        outbox.append(event.eventId(), AGGREGATE_TYPE, transfer.id(), TransferRecordedEvent.TYPE, serialize(event),
                event.occurredAt());
    }

    private String serialize(TransferRecordedEvent event) {
        try {
            return json.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize event " + event.eventId(), e);
        }
    }
}
