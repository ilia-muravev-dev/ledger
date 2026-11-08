package dev.iliamuravev.ledger.domain;

import java.time.Instant;
import java.util.UUID;

final class AccountFixtures {

    private AccountFixtures() {
    }

    static Account customer(String currency, long balance) {
        return account(AccountType.CUSTOMER, AccountStatus.ACTIVE, currency, balance);
    }

    static Account closedCustomer(String currency, long balance) {
        return account(AccountType.CUSTOMER, AccountStatus.CLOSED, currency, balance);
    }

    static Account settlement(String currency, long balance) {
        return account(AccountType.SETTLEMENT, AccountStatus.ACTIVE, currency, balance);
    }

    private static Account account(AccountType type, AccountStatus status, String currency, long balance) {
        var money = Money.of(balance, currency);
        return new Account(UUID.randomUUID(), "owner", money.currency(), type, status, money, 0,
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}
