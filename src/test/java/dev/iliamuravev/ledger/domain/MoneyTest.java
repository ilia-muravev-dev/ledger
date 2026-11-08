package dev.iliamuravev.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    @Test
    void addsAndSubtractsInMinorUnits() {
        var a = Money.of(1_050, "EUR");
        var b = Money.of(25, "EUR");

        assertThat(a.plus(b)).isEqualTo(Money.of(1_075, "EUR"));
        assertThat(a.minus(b)).isEqualTo(Money.of(1_025, "EUR"));
        assertThat(b.minus(a)).isEqualTo(Money.of(-1_025, "EUR"));
        assertThat(a.negate()).isEqualTo(Money.of(-1_050, "EUR"));
    }

    @Test
    void rejectsArithmeticAcrossCurrencies() {
        var eur = Money.of(100, "EUR");
        var usd = Money.of(100, "USD");

        assertThatThrownBy(() -> eur.plus(usd)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> eur.minus(usd)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> eur.isLessThan(usd)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failsLoudlyOnOverflowInsteadOfWrappingAround() {
        var max = Money.of(Long.MAX_VALUE, "EUR");

        assertThatThrownBy(() -> max.plus(Money.of(1, "EUR"))).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> Money.of(Long.MIN_VALUE, "EUR").negate()).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void comparesAndClassifiesSign() {
        assertThat(Money.of(1, "EUR").isPositive()).isTrue();
        assertThat(Money.of(-1, "EUR").isNegative()).isTrue();
        assertThat(Money.of(0, "EUR").isZero()).isTrue();
        assertThat(Money.of(99, "EUR").isLessThan(Money.of(100, "EUR"))).isTrue();
        assertThat(Money.of(100, "EUR").isLessThan(Money.of(100, "EUR"))).isFalse();
    }

    @Test
    void formatsUsingTheCurrencyMinorUnitScale() {
        assertThat(Money.of(123_456, "EUR")).hasToString("1234.56 EUR");
        assertThat(Money.of(-5, "USD")).hasToString("-0.05 USD");
        assertThat(Money.of(1_500, "JPY")).hasToString("1500 JPY");
        assertThat(Money.of(1_234, "KWD")).hasToString("1.234 KWD");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"eur", "EU", "EURO", "ABC", "12€"})
    void rejectsInvalidCurrencyCodes(String code) {
        assertThatThrownBy(() -> Money.of(1, code)).isInstanceOf(InvalidRequestException.class);
    }
}
