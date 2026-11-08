package dev.iliamuravev.ledger.domain;

import java.time.Instant;
import java.util.UUID;

public record LedgerEntry(long id, UUID transferId, UUID accountId, Money amount, Instant createdAt) {
}
