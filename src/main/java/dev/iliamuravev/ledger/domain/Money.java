package dev.iliamuravev.ledger.domain;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

public record Money(long amount, Currency currency) {

    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    public static Money of(long amount, String currencyCode) {
        return new Money(amount, CurrencyCodes.parse(currencyCode));
    }

    public static Money zero(Currency currency) {
        return new Money(0, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(amount, other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(amount, other.amount), currency);
    }

    public Money negate() {
        return new Money(Math.negateExact(amount), currency);
    }

    public boolean isPositive() {
        return amount > 0;
    }

    public boolean isNegative() {
        return amount < 0;
    }

    public boolean isZero() {
        return amount == 0;
    }

    public boolean isLessThan(Money other) {
        requireSameCurrency(other);
        return amount < other.amount;
    }

    public String currencyCode() {
        return currency.getCurrencyCode();
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: " + currencyCode() + " vs " + other.currencyCode());
        }
    }

    @Override
    public String toString() {
        int scale = Math.max(currency.getDefaultFractionDigits(), 0);
        return BigDecimal.valueOf(amount, scale).toPlainString() + " " + currencyCode();
    }
}
