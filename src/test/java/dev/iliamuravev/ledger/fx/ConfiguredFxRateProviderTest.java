package dev.iliamuravev.ledger.fx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import dev.iliamuravev.ledger.config.LedgerClock;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfiguredFxRateProviderTest {

    private final LedgerClock clock =
            new LedgerClock(Clock.fixed(Instant.parse("2026-11-08T10:00:00Z"), ZoneOffset.UTC));
    private final ConfiguredFxRateProvider rates = new ConfiguredFxRateProvider(new FxProperties("test",
            Map.of("USD", BigDecimal.ONE, "EUR", new BigDecimal("1.0850"), "JPY", new BigDecimal("0.0067"))),
            clock);

    @Test
    void quotesDirectAndCrossRatesThroughUsd() {
        assertThat(rate("EUR", "USD")).isEqualByComparingTo("1.0850");
        assertThat(rate("USD", "EUR")).isEqualByComparingTo("0.921658986175");
        assertThat(rate("EUR", "JPY")).isEqualByComparingTo("161.940298507463");
    }

    @Test
    void roundTripIsCloseToOne() {
        var product = rate("EUR", "JPY").multiply(rate("JPY", "EUR"), MathContext.DECIMAL64);

        assertThat(product).isCloseTo(BigDecimal.ONE, offset(new BigDecimal("1e-9")));
    }

    @Test
    void snapshotCarriesSourceAndTime() {
        var quote = rates.quote(currency("EUR"), currency("USD")).orElseThrow();

        assertThat(quote.source()).isEqualTo("test");
        assertThat(quote.quotedAt()).isEqualTo(Instant.parse("2026-11-08T10:00:00Z"));
    }

    @Test
    void unknownCurrencyOrSameCurrencyHasNoQuote() {
        assertThat(rates.quote(currency("EUR"), currency("SEK"))).isEmpty();
        assertThat(rates.quote(currency("EUR"), currency("EUR"))).isEmpty();
    }

    @Test
    void rejectsBadConfiguration() {
        assertThatThrownBy(() -> new ConfiguredFxRateProvider(
                new FxProperties("test", Map.of("EUR", BigDecimal.ZERO)), clock))
                .isInstanceOf(IllegalStateException.class);
    }

    private BigDecimal rate(String from, String to) {
        return rates.quote(currency(from), currency(to)).orElseThrow().rate();
    }

    private static Currency currency(String code) {
        return Currency.getInstance(code);
    }
}
