package dev.iliamuravev.ledger.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransferRecordedEvent(UUID eventId, Instant occurredAt, TransferData transfer, List<EntryData> entries) {

    public static final String TYPE = "TransferRecorded";

    public TransferRecordedEvent {
        entries = List.copyOf(entries);
    }

    public record TransferData(
            UUID id,
            String type,
            UUID fromAccountId,
            UUID toAccountId,
            long amount,
            String currency,
            String status,
            String reason,
            Instant createdAt) {
    }

    public record EntryData(
            long entryId,
            UUID accountId,
            UUID counterpartyAccountId,
            long amount,
            String currency,
            long balanceAfter) {
    }
}
