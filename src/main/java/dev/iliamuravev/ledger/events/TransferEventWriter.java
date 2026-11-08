package dev.iliamuravev.ledger.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.iliamuravev.ledger.config.LedgerClock;
import dev.iliamuravev.ledger.domain.Posting;
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
    public void transferRecorded(Transfer transfer, List<Posting> postings, Map<UUID, Long> entryIds,
            Map<UUID, Long> balancesAfter) {
        var entries = postings.stream()
                .map(p -> new EntryData(
                        required(entryIds, p.accountId()),
                        p.accountId(),
                        p.counterpartyAccountId(),
                        p.amount().amount(),
                        p.amount().currencyCode(),
                        required(balancesAfter, p.accountId())))
                .toList();
        var fx = transfer.fx();
        var event = new TransferRecordedEvent(UUID.randomUUID(), clock.now(), new TransferData(
                transfer.id(),
                transfer.type().name(),
                transfer.fromAccountId(),
                transfer.toAccountId(),
                transfer.amount().amount(),
                transfer.amount().currencyCode(),
                transfer.status().name(),
                transfer.reason() == null ? null : transfer.reason().name(),
                fx == null ? null : fx.target().amount(),
                fx == null ? null : fx.target().currencyCode(),
                fx == null ? null : fx.rate(),
                fx == null ? null : fx.rateSource(),
                transfer.createdAt()), entries);
        outbox.append(event.eventId(), AGGREGATE_TYPE, transfer.id(), TransferRecordedEvent.TYPE, serialize(event),
                event.occurredAt());
    }

    private static long required(Map<UUID, Long> values, UUID accountId) {
        var value = values.get(accountId);
        if (value == null) {
            throw new IllegalStateException("Missing value for account " + accountId);
        }
        return value;
    }

    private String serialize(TransferRecordedEvent event) {
        try {
            return json.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize event " + event.eventId(), e);
        }
    }
}
