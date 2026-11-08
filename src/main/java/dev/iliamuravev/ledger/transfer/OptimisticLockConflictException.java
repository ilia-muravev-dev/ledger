package dev.iliamuravev.ledger.transfer;

import java.util.UUID;

class OptimisticLockConflictException extends RuntimeException {

    OptimisticLockConflictException(UUID accountId) {
        super("Account " + accountId + " was modified concurrently", null, false, false);
    }
}
