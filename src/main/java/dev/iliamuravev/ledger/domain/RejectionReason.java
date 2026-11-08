package dev.iliamuravev.ledger.domain;

public enum RejectionReason {
    SETTLEMENT_ACCOUNT_NOT_ALLOWED,
    ACCOUNT_CLOSED,
    CURRENCY_MISMATCH,
    INSUFFICIENT_FUNDS,
    FX_RATE_UNAVAILABLE
}
