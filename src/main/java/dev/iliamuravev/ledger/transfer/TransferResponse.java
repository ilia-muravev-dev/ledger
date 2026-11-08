package dev.iliamuravev.ledger.transfer;

import dev.iliamuravev.ledger.domain.Transfer;
import java.time.Instant;
import java.util.UUID;

record TransferResponse(
        UUID id,
        String type,
        UUID fromAccountId,
        UUID toAccountId,
        long amount,
        String currency,
        String status,
        String reason,
        Instant createdAt) {

    static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.id(),
                transfer.type().name(),
                transfer.fromAccountId(),
                transfer.toAccountId(),
                transfer.amount().amount(),
                transfer.amount().currencyCode(),
                transfer.status().name(),
                transfer.reason() == null ? null : transfer.reason().name(),
                transfer.createdAt());
    }
}
