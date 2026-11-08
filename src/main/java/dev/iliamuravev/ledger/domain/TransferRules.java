package dev.iliamuravev.ledger.domain;

import java.util.Optional;

public final class TransferRules {

    private TransferRules() {
    }

    public static Optional<RejectionReason> evaluate(TransferCommand command, Account from, Account to) {
        if (!command.fromAccountId().equals(from.id()) || !command.toAccountId().equals(to.id())) {
            throw new IllegalArgumentException("Accounts do not match the command");
        }
        if (!accountTypesAllowed(command.type(), from, to)) {
            return Optional.of(RejectionReason.SETTLEMENT_ACCOUNT_NOT_ALLOWED);
        }
        if (!from.isActive() || !to.isActive()) {
            return Optional.of(RejectionReason.ACCOUNT_CLOSED);
        }
        if (!from.currency().equals(command.amount().currency())
                || !to.currency().equals(command.creditCurrency())) {
            return Optional.of(RejectionReason.CURRENCY_MISMATCH);
        }
        if (from.isCustomer() && from.balance().isLessThan(command.amount())) {
            return Optional.of(RejectionReason.INSUFFICIENT_FUNDS);
        }
        return Optional.empty();
    }

    private static boolean accountTypesAllowed(TransferType type, Account from, Account to) {
        return switch (type) {
            case TRANSFER -> from.isCustomer() && to.isCustomer();
            case DEPOSIT -> from.isSettlement() && to.isCustomer();
        };
    }
}
