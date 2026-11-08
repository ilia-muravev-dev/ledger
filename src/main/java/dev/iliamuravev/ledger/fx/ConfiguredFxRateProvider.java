package dev.iliamuravev.ledger.fx;

import dev.iliamuravev.ledger.config.LedgerClock;
import dev.iliamuravev.ledger.domain.FxQuote;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(FxProperties.class)
class ConfiguredFxRateProvider implements FxRateProvider {

    static final int RATE_SCALE = 12;

    private final FxProperties properties;
    private final LedgerClock clock;

    ConfiguredFxRateProvider(FxProperties properties, LedgerClock clock) {
        this.properties = properties;
        this.clock = clock;
        properties.usdPerUnit().forEach((code, rate) -> {
            Currency.getInstance(code);
            if (rate.signum() <= 0) {
                throw new IllegalStateException("FX rate for " + code + " must be positive");
            }
        });
    }

    @Override
    public Optional<FxQuote> quote(Currency from, Currency to) {
        var fromUsd = properties.usdPerUnit().get(from.getCurrencyCode());
        var toUsd = properties.usdPerUnit().get(to.getCurrencyCode());
        if (fromUsd == null || toUsd == null || from.equals(to)) {
            return Optional.empty();
        }
        var rate = fromUsd.divide(toUsd, MathContext.DECIMAL128).setScale(RATE_SCALE, RoundingMode.HALF_EVEN);
        return Optional.of(new FxQuote(from, to, rate, properties.source(), clock.now()));
    }

    BigDecimal usdPerUnit(String code) {
        return properties.usdPerUnit().get(code);
    }
}
