package dev.iliamuravev.ledger.transfer;

import java.util.Comparator;
import java.util.UUID;

final class LockOrder {

    static final Comparator<UUID> ACCOUNTS = Comparator.comparing(UUID::toString);

    private LockOrder() {
    }
}
