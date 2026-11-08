package dev.iliamuravev.ledger.domain;

import java.util.UUID;

public class AccountNotFoundException extends LedgerException {

    private final UUID accountId;

    public AccountNotFoundException(UUID accountId) {
        super("Account " + accountId + " does not exist");
        this.accountId = accountId;
    }

    public UUID accountId() {
        return accountId;
    }
}
