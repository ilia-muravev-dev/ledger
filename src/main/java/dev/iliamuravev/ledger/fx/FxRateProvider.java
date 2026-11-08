package dev.iliamuravev.ledger.fx;

import dev.iliamuravev.ledger.domain.FxQuote;
import java.util.Currency;
import java.util.Optional;

public interface FxRateProvider {

    Optional<FxQuote> quote(Currency from, Currency to);
}
