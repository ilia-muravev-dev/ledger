package dev.iliamuravev.ledger.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Posting(UUID accountId, UUID counterpartyAccountId, Money amount) {

    public Posting {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(counterpartyAccountId, "counterpartyAccountId");
        Objects.requireNonNull(amount, "amount");
        if (amount.isZero()) {
            throw new IllegalArgumentException("A posting must move money");
        }
    }

    public static List<Posting> forTransfer(UUID from, UUID to, Money amount) {
        return List.of(new Posting(from, to, amount.negate()), new Posting(to, from, amount));
    }

    public static List<Posting> forFxTransfer(UUID from, UUID to, Money source, UUID sourcePosition,
            UUID targetPosition, Money target) {
        return List.of(
                new Posting(from, to, source.negate()),
                new Posting(sourcePosition, from, source),
                new Posting(targetPosition, to, target.negate()),
                new Posting(to, from, target));
    }
}
