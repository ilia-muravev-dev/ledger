package dev.iliamuravev.ledger.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

public record FxQuote(Currency from, Currency to, BigDecimal rate, String source, Instant quotedAt) {

    public FxQuote {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(quotedAt, "quotedAt");
        if (rate.signum() <= 0) {
            throw new IllegalArgumentException("FX rate must be positive");
        }
        if (from.equals(to)) {
            throw new IllegalArgumentException("FX quote needs two different currencies");
        }
    }
}
