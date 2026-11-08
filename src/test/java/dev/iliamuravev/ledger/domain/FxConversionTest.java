package dev.iliamuravev.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FxConversionTest {

    @Test
    void convertsBetweenTwoDecimalCurrencies() {
        var conversion = FxConversion.convert(Money.of(10_000, "EUR"), quote("EUR", "USD", "1.0850"));

        assertThat(conversion.target()).isEqualTo(Money.of(10_850, "USD"));
        assertThat(conversion.rate()).isEqualByComparingTo("1.0850");
        assertThat(conversion.rateSource()).isEqualTo("test");
    }

    @Test
    void scalesBetweenCurrenciesWithDifferentMinorUnits() {
        assertThat(FxConversion.convert(Money.of(100, "USD"), quote("USD", "JPY", "149.2537313433")).target())
                .isEqualTo(Money.of(149, "JPY"));
        assertThat(FxConversion.convert(Money.of(1_000, "JPY"), quote("JPY", "USD", "0.0067")).target())
                .isEqualTo(Money.of(670, "USD"));
        assertThat(FxConversion.convert(Money.of(1_000, "USD"), quote("USD", "KWD", "0.3070")).target())
                .isEqualTo(Money.of(3_070, "KWD"));
    }

    @ParameterizedTest
    @CsvSource({"150, 2", "250, 2", "350, 4", "149, 1", "151, 2"})
    void roundsHalfToEven(long sourceCents, long expectedYen) {
        var conversion = FxConversion.convert(Money.of(sourceCents, "EUR"), quote("EUR", "JPY", "1"));

        assertThat(conversion.target()).isEqualTo(Money.of(expectedYen, "JPY"));
    }

    @Test
    void refusesAmountsThatConvertToNothing() {
        assertThatThrownBy(() -> FxConversion.convert(Money.of(1, "JPY"), quote("JPY", "EUR", "0.001")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("less than one minor unit");
    }

    @Test
    void refusesAQuoteForAnotherCurrency() {
        assertThatThrownBy(() -> FxConversion.convert(Money.of(1, "GBP"), quote("EUR", "USD", "1.1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void quoteNeedsAPositiveRateAndTwoCurrencies() {
        assertThatThrownBy(() -> quote("EUR", "USD", "0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> quote("EUR", "EUR", "1")).isInstanceOf(IllegalArgumentException.class);
    }

    private static FxQuote quote(String from, String to, String rate) {
        return new FxQuote(Currency.getInstance(from), Currency.getInstance(to), new BigDecimal(rate), "test",
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}
