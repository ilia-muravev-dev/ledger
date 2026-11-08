package dev.iliamuravev.ledger.fx;

import dev.iliamuravev.ledger.domain.CurrencyCodes;
import dev.iliamuravev.ledger.domain.InvalidRequestException;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class FxController {

    private final FxRateProvider rates;

    FxController(FxRateProvider rates) {
        this.rates = rates;
    }

    @GetMapping("/fx/rates")
    RateResponse rate(@RequestParam String from, @RequestParam String to) {
        var quote = rates.quote(CurrencyCodes.parse(from), CurrencyCodes.parse(to))
                .orElseThrow(() -> new InvalidRequestException("No FX rate for " + from + " to " + to));
        return new RateResponse(from, to, quote.rate(), quote.source(), quote.quotedAt());
    }

    record RateResponse(String from, String to, BigDecimal rate, String source, Instant quotedAt) {
    }
}
