package dev.iliamuravev.ledger.transfer;

import dev.iliamuravev.ledger.account.AccountRepository;
import dev.iliamuravev.ledger.domain.Account;
import dev.iliamuravev.ledger.domain.AccountNotFoundException;
import dev.iliamuravev.ledger.domain.TransferCommand;
import dev.iliamuravev.ledger.domain.TransferRules;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class OptimisticTransferEngine implements TransferEngine {

    private final TransactionTemplate tx;
    private final AccountRepository accounts;
    private final TransferRecorder recorder;
    private final RetryPolicy retryPolicy;
    private final Counter conflicts;

    @Autowired
    OptimisticTransferEngine(TransactionTemplate tx, AccountRepository accounts, TransferRecorder recorder,
            TransferProperties properties, MeterRegistry meters) {
        this(tx, accounts, recorder, properties.optimistic().retryPolicy(), meters);
    }

    OptimisticTransferEngine(TransactionTemplate tx, AccountRepository accounts, TransferRecorder recorder,
            RetryPolicy retryPolicy, MeterRegistry meters) {
        this.tx = tx;
        this.accounts = accounts;
        this.recorder = recorder;
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

        var from = read(command.fromAccountId());
        var to = read(command.toAccountId());

        var rejection = TransferRules.evaluate(command, from, to);
        if (rejection.isEmpty()) {
            var amount = command.amount().amount();
            Stream.of(new Delta(from, -amount), new Delta(to, amount))
                    .sorted(Comparator.comparing(delta -> delta.account.id(), LockOrder.ACCOUNTS))
                    .forEach(this::apply);
        }
        return TransferOutcome.created(recorder.record(command, from, to, rejection));
    }

    private Account read(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    private void apply(Delta delta) {
        if (!accounts.addToBalanceIfVersion(delta.account.id(), delta.amount, delta.account.version())) {
            throw new OptimisticLockConflictException(delta.account.id());
        }
    }

    private void pause(int failedAttempts) {
        try {
            Thread.sleep(retryPolicy.backoff(failedAttempts));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransferContentionException(failedAttempts);
        }
    }

    private record Delta(Account account, long amount) {
    }
}
