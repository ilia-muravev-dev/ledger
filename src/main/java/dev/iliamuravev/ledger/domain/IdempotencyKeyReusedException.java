package dev.iliamuravev.ledger.domain;

public class IdempotencyKeyReusedException extends LedgerException {

    private final String idempotencyKey;

    public IdempotencyKeyReusedException(String idempotencyKey) {
        super("Idempotency key '" + idempotencyKey + "' was already used with a different request");
        this.idempotencyKey = idempotencyKey;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }
}
