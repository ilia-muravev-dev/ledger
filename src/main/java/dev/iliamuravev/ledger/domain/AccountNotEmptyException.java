package dev.iliamuravev.ledger.domain;

import java.util.UUID;

public class AccountNotEmptyException extends LedgerException {

    private final UUID accountId;

    public AccountNotEmptyException(UUID accountId) {
        super("Account " + accountId + " can only be closed with a zero balance");
        this.accountId = accountId;
    }

    public UUID accountId() {
        return accountId;
    }
}
