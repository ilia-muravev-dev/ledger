package dev.iliamuravev.ledger.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

public record FxConversion(Money target, BigDecimal rate, String rateSource, Instant quotedAt) {

    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    public FxConversion {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(rateSource, "rateSource");
        Objects.requireNonNull(quotedAt, "quotedAt");
    }

    public static FxConversion convert(Money source, FxQuote quote) {
        if (!source.currency().equals(quote.from())) {
            throw new IllegalArgumentException("Quote is for " + quote.from() + ", amount is in " + source.currency());
        }
        var shift = minorDigits(quote.to()) - minorDigits(quote.from());
        var converted = BigDecimal.valueOf(source.amount())
                .multiply(quote.rate())
                .scaleByPowerOfTen(shift)
                .setScale(0, ROUNDING);
        if (converted.signum() <= 0) {
            throw new InvalidRequestException(
                    source + " is worth less than one minor unit of " + quote.to().getCurrencyCode());
        }
        return new FxConversion(new Money(converted.longValueExact(), quote.to()), quote.rate(), quote.source(),
                quote.quotedAt());
    }

    private static int minorDigits(Currency currency) {
        return Math.max(currency.getDefaultFractionDigits(), 0);
    }
}
