package dev.iliamuravev.ledger.domain;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

public record Account(
        UUID id,
        String owner,
        Currency currency,
        AccountType type,
        AccountStatus status,
        Money balance,
        long version,
        Instant createdAt) {

    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(balance, "balance");
        Objects.requireNonNull(createdAt, "createdAt");
        if (!balance.currency().equals(currency)) {
            throw new IllegalArgumentException("Balance currency must match account currency");
        }
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean isSettlement() {
        return type == AccountType.SETTLEMENT;
    }
}
