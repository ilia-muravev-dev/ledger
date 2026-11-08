package dev.iliamuravev.ledger.transfer;

import dev.iliamuravev.ledger.domain.FxConversion;
import dev.iliamuravev.ledger.domain.FxRoute;
import dev.iliamuravev.ledger.domain.Posting;
import dev.iliamuravev.ledger.domain.RejectionReason;
import dev.iliamuravev.ledger.domain.TransferCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

record FxRouting(Optional<FxRoute> route, Optional<RejectionReason> rejection) {

    static final FxRouting NONE = new FxRouting(Optional.empty(), Optional.empty());

    static FxRouting unavailable() {
        return new FxRouting(Optional.empty(), Optional.of(RejectionReason.FX_RATE_UNAVAILABLE));
    }

    static FxRouting via(FxRoute route) {
        return new FxRouting(Optional.of(route), Optional.empty());
    }

    List<UUID> accountIds(TransferCommand command) {
        var ids = new ArrayList<>(List.of(command.fromAccountId(), command.toAccountId()));
        route.ifPresent(r -> {
            ids.add(r.sourcePositionId());
            ids.add(r.targetPositionId());
        });
        return ids;
    }

    List<Posting> postings(TransferCommand command) {
        return route
                .map(r -> Posting.forFxTransfer(command.fromAccountId(), command.toAccountId(), command.amount(),
                        r.sourcePositionId(), r.targetPositionId(), r.conversion().target()))
                .orElseGet(() -> Posting.forTransfer(command.fromAccountId(), command.toAccountId(),
                        command.amount()));
    }

    FxConversion conversion() {
        return route.map(FxRoute::conversion).orElse(null);
    }
}
