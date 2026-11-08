package dev.iliamuravev.ledger.domain;

public abstract class LedgerException extends RuntimeException {

    protected LedgerException(String message) {
        super(message);
    }
}
