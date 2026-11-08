package dev.iliamuravev.ledger.transfer;

import dev.iliamuravev.ledger.account.AccountRepository;
import dev.iliamuravev.ledger.domain.Account;
import dev.iliamuravev.ledger.domain.AccountNotFoundException;
import dev.iliamuravev.ledger.domain.Posting;
import dev.iliamuravev.ledger.domain.TransferCommand;
import dev.iliamuravev.ledger.domain.TransferRules;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class OptimisticTransferEngine implements TransferEngine {

    private final TransactionTemplate tx;
    private final AccountRepository accounts;
    private final TransferRecorder recorder;
    private final FxRouter fxRouter;
    private final RetryPolicy retryPolicy;
    private final Counter conflicts;

    @Autowired
    OptimisticTransferEngine(TransactionTemplate tx, AccountRepository accounts, TransferRecorder recorder,
            FxRouter fxRouter, TransferProperties properties, MeterRegistry meters) {
        this(tx, accounts, recorder, fxRouter, properties.optimistic().retryPolicy(), meters);
    }

    OptimisticTransferEngine(TransactionTemplate tx, AccountRepository accounts, TransferRecorder recorder,
            FxRouter fxRouter, RetryPolicy retryPolicy, MeterRegistry meters) {
        this.tx = tx;
        this.accounts = accounts;
        this.recorder = recorder;
        this.fxRouter = fxRouter;
        this.retryPolicy = retryPolicy;
        this.conflicts = Counter.builder("ledger.transfers.optimistic.conflicts")
                .description("Optimistic transfer attempts rolled back because an account version changed")
                .register(meters);
    }

    @Override
    public TransferOutcome execute(TransferCommand command) {
        for (int attempt = 1; ; attempt++) {
            try {
                return tx.execute(status -> executeInTransaction(command));
            } catch (OptimisticLockConflictException e) {
                conflicts.increment();
                if (attempt >= retryPolicy.maxAttempts()) {
                    throw new TransferContentionException(attempt);
                }
                pause(attempt);
            }
        }
    }

    private TransferOutcome executeInTransaction(TransferCommand command) {
        var replay = recorder.claimOrReplay(command);
        if (replay.isPresent()) {
            return replay.get();
        }

        var fx = fxRouter.route(command);
        var read = new HashMap<UUID, Account>();
        fx.accountIds(command).forEach(id -> read.put(id, read(id)));
        var from = read.get(command.fromAccountId());
        var to = read.get(command.toAccountId());

        var rejection = TransferRules.evaluate(command, from, to).or(fx::rejection);
        List<Posting> postings = List.of();
        var balancesAfter = new HashMap<UUID, Long>();
        if (rejection.isEmpty()) {
            postings = fx.postings(command);
            postings.stream()
                    .sorted(Comparator.comparing(Posting::accountId, LockOrder.ACCOUNTS))
                    .forEach(p -> balancesAfter.put(p.accountId(), apply(read.get(p.accountId()), p)));
        }
        return TransferOutcome.created(recorder.record(command, fx.conversion(), postings, balancesAfter, rejection));
    }

    private Account read(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    private long apply(Account account, Posting posting) {
        if (!account.isCustomer()) {
            return accounts.addToBalance(account.id(), posting.amount().amount());
        }
        return accounts.addToBalanceIfVersion(account.id(), posting.amount().amount(), account.version())
                .orElseThrow(() -> new OptimisticLockConflictException(account.id()));
    }

    private void pause(int failedAttempts) {
        try {
            Thread.sleep(retryPolicy.backoff(failedAttempts));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransferContentionException(failedAttempts);
        }
    }

}
