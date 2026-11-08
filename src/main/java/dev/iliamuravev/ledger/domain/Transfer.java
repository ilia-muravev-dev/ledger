package dev.iliamuravev.ledger.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Transfer(
        UUID id,
        String idempotencyKey,
        TransferType type,
        UUID fromAccountId,
        UUID toAccountId,
        Money amount,
        TransferStatus status,
        RejectionReason reason,
        Instant createdAt) {

    public Transfer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(fromAccountId, "fromAccountId");
        Objects.requireNonNull(toAccountId, "toAccountId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if ((status == TransferStatus.REJECTED) != (reason != null)) {
            throw new IllegalArgumentException("A reason is required for rejected transfers and only for them");
        }
    }

    public static Transfer completed(UUID id, TransferCommand command, Instant createdAt) {
        return new Transfer(id, command.idempotencyKey(), command.type(), command.fromAccountId(),
                command.toAccountId(), command.amount(), TransferStatus.COMPLETED, null, createdAt);
    }

    public static Transfer rejected(UUID id, TransferCommand command, RejectionReason reason, Instant createdAt) {
        return new Transfer(id, command.idempotencyKey(), command.type(), command.fromAccountId(),
                command.toAccountId(), command.amount(), TransferStatus.REJECTED, reason, createdAt);
    }

    public boolean isCompleted() {
        return status == TransferStatus.COMPLETED;
    }
}
