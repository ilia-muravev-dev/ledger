package dev.iliamuravev.ledger.domain;

import static dev.iliamuravev.ledger.domain.AccountFixtures.closedCustomer;
import static dev.iliamuravev.ledger.domain.AccountFixtures.customer;
import static dev.iliamuravev.ledger.domain.AccountFixtures.settlement;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TransferRulesTest {

    @Test
    void allowsTransferBetweenActiveAccountsWithSufficientFunds() {
        var from = customer("EUR", 1_000);
        var to = customer("EUR", 0);

        assertThat(TransferRules.evaluate(transfer(from, to, 1_000), from, to)).isEmpty();
    }

    @Test
    void rejectsWhenBalanceIsInsufficient() {
        var from = customer("EUR", 999);
        var to = customer("EUR", 0);

        assertThat(TransferRules.evaluate(transfer(from, to, 1_000), from, to))
                .contains(RejectionReason.INSUFFICIENT_FUNDS);
    }

    @Test
    void rejectsWhenEitherAccountIsClosed() {
        var active = customer("EUR", 1_000);
        var closed = closedCustomer("EUR", 1_000);

        assertThat(TransferRules.evaluate(transfer(closed, active, 1), closed, active))
                .contains(RejectionReason.ACCOUNT_CLOSED);
        assertThat(TransferRules.evaluate(transfer(active, closed, 1), active, closed))
                .contains(RejectionReason.ACCOUNT_CLOSED);
    }

    @Test
    void rejectsCurrencyMismatchBetweenAccountsOrWithTheAmount() {
        var eur = customer("EUR", 1_000);
        var usd = customer("USD", 1_000);
        var otherEur = customer("EUR", 0);

        assertThat(TransferRules.evaluate(transfer(eur, usd, 1), eur, usd))
                .contains(RejectionReason.CURRENCY_MISMATCH);

        var usdAmountBetweenEurAccounts = new TransferCommand("k", TransferType.TRANSFER, eur.id(), otherEur.id(),
                Money.of(1, "USD"));
        assertThat(TransferRules.evaluate(usdAmountBetweenEurAccounts, eur, otherEur))
                .contains(RejectionReason.CURRENCY_MISMATCH);
    }

    @Test
    void closedAccountWinsOverInsufficientFunds() {
        var closed = closedCustomer("EUR", 0);
        var to = customer("EUR", 0);

        assertThat(TransferRules.evaluate(transfer(closed, to, 100), closed, to))
                .contains(RejectionReason.ACCOUNT_CLOSED);
    }

    @Test
    void customerTransfersCannotTouchSettlementAccounts() {
        var customer = customer("EUR", 1_000);
        var settlement = settlement("EUR", -1_000);

        assertThat(TransferRules.evaluate(transfer(settlement, customer, 1), settlement, customer))
                .contains(RejectionReason.SETTLEMENT_ACCOUNT_NOT_ALLOWED);
        assertThat(TransferRules.evaluate(transfer(customer, settlement, 1), customer, settlement))
                .contains(RejectionReason.SETTLEMENT_ACCOUNT_NOT_ALLOWED);
    }

    @Test
    void depositsComeFromSettlementWhichMayGoNegative() {
        var settlement = settlement("EUR", 0);
        var customer = customer("EUR", 0);

        assertThat(TransferRules.evaluate(deposit(settlement, customer, 5_000), settlement, customer)).isEmpty();
    }

    @Test
    void depositsMustComeFromSettlementIntoACustomerAccount() {
        var a = customer("EUR", 1_000);
        var b = customer("EUR", 0);

        assertThat(TransferRules.evaluate(deposit(a, b, 1), a, b))
                .contains(RejectionReason.SETTLEMENT_ACCOUNT_NOT_ALLOWED);
    }

    @Test
    void refusesAccountsThatDoNotBelongToTheCommand() {
        var from = customer("EUR", 1_000);
        var to = customer("EUR", 0);
        var stranger = customer("EUR", 0);

        assertThatThrownBy(() -> TransferRules.evaluate(transfer(from, to, 1), from, stranger))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static TransferCommand transfer(Account from, Account to, long amount) {
        return new TransferCommand("k", TransferType.TRANSFER, from.id(), to.id(),
                new Money(amount, from.currency()));
    }

    private static TransferCommand deposit(Account from, Account to, long amount) {
        return new TransferCommand("k", TransferType.DEPOSIT, from.id(), to.id(),
                new Money(amount, from.currency()));
    }
}
