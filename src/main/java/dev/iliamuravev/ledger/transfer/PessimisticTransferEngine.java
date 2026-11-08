package dev.iliamuravev.ledger.transfer;

import dev.iliamuravev.ledger.account.AccountRepository;
import dev.iliamuravev.ledger.domain.Account;
import dev.iliamuravev.ledger.domain.AccountNotFoundException;
import dev.iliamuravev.ledger.domain.Posting;
import dev.iliamuravev.ledger.domain.TransferCommand;
import dev.iliamuravev.ledger.domain.TransferRules;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class PessimisticTransferEngine implements TransferEngine {

    private final TransactionTemplate tx;
    private final AccountRepository accounts;
    private final TransferRecorder recorder;
    private final FxRouter fxRouter;

    PessimisticTransferEngine(TransactionTemplate tx, AccountRepository accounts, TransferRecorder recorder,
            FxRouter fxRouter) {
        this.tx = tx;
        this.accounts = accounts;
        this.recorder = recorder;
        this.fxRouter = fxRouter;
    }

    @Override
    public TransferOutcome execute(TransferCommand command) {
        return tx.execute(status -> executeInTransaction(command));
    }

    private TransferOutcome executeInTransaction(TransferCommand command) {
        var replay = recorder.claimOrReplay(command);
        if (replay.isPresent()) {
            return replay.get();
        }

        var fx = fxRouter.route(command);
        var locked = lockInOrder(fx.accountIds(command));
        var from = locked.get(command.fromAccountId());
        var to = locked.get(command.toAccountId());

        var rejection = TransferRules.evaluate(command, from, to).or(fx::rejection);
        List<Posting> postings = List.of();
        var balancesAfter = new HashMap<UUID, Long>();
        if (rejection.isEmpty()) {
            postings = fx.postings(command);
            postings.stream()
                    .sorted(Comparator.comparing(Posting::accountId, LockOrder.ACCOUNTS))
                    .forEach(p -> balancesAfter.put(p.accountId(),
                            accounts.addToBalance(p.accountId(), p.amount().amount())));
        }
        return TransferOutcome.created(recorder.record(command, fx.conversion(), postings, balancesAfter, rejection));
    }

    private Map<UUID, Account> lockInOrder(List<UUID> ids) {
        var locked = new HashMap<UUID, Account>();
        ids.stream()
                .distinct()
                .sorted(LockOrder.ACCOUNTS)
                .forEach(id -> locked.put(id, accounts.lockById(id)
                        .orElseThrow(() -> new AccountNotFoundException(id))));
        return locked;
    }
}
