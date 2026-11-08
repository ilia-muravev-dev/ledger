package dev.iliamuravev.ledger.live;

import java.time.Instant;
import java.util.UUID;

public record LiveUpdate(
        String type,
        UUID accountId,
        long entryId,
        UUID transferId,
        String transferType,
        UUID counterpartyAccountId,
        long amount,
        String currency,
        long balanceAfter,
        Instant postedAt) {

    public static final String ENTRY_POSTED = "entry-posted";
}
