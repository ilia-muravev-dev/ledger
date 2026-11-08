package dev.iliamuravev.ledger.transfer;

import dev.iliamuravev.ledger.account.AccountRepository;
import dev.iliamuravev.ledger.config.LedgerClock;
import dev.iliamuravev.ledger.domain.AccountType;
import dev.iliamuravev.ledger.domain.FxConversion;
import dev.iliamuravev.ledger.domain.FxRoute;
import dev.iliamuravev.ledger.domain.TransferCommand;
import dev.iliamuravev.ledger.fx.FxRateProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class FxRouter {

    private final FxRateProvider rates;
    private final AccountRepository accounts;
    private final LedgerClock clock;

    FxRouter(FxRateProvider rates, AccountRepository accounts, LedgerClock clock) {
        this.rates = rates;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public FxRouting route(TransferCommand command) {
        if (!command.isFx()) {
            return FxRouting.NONE;
        }
        var quote = rates.quote(command.amount().currency(), command.targetCurrency());
        if (quote.isEmpty()) {
            return FxRouting.unavailable();
        }
        var conversion = FxConversion.convert(command.amount(), quote.get());
        var now = clock.now();
        var sourcePosition = accounts.systemAccount(AccountType.FX_POSITION, command.amount().currency(), now);
        var targetPosition = accounts.systemAccount(AccountType.FX_POSITION, command.targetCurrency(), now);
        return FxRouting.via(new FxRoute(sourcePosition.id(), targetPosition.id(), conversion));
    }
}
