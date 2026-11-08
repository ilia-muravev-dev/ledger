package dev.iliamuravev.ledger.events;

import java.math.BigDecimal;
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
            Long targetAmount,
            String targetCurrency,
            BigDecimal fxRate,
            String fxRateSource,
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
