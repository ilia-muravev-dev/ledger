package dev.iliamuravev.ledger.transfer;

import dev.iliamuravev.ledger.account.AccountRepository;
import dev.iliamuravev.ledger.domain.Account;
import dev.iliamuravev.ledger.domain.AccountNotFoundException;
import dev.iliamuravev.ledger.domain.TransferCommand;
import dev.iliamuravev.ledger.domain.TransferRules;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class PessimisticTransferEngine implements TransferEngine {

    static final Comparator<UUID> LOCK_ORDER = Comparator.comparing(UUID::toString);

    private final TransactionTemplate tx;
    private final AccountRepository accounts;
    private final TransferRecorder recorder;

    PessimisticTransferEngine(TransactionTemplate tx, AccountRepository accounts, TransferRecorder recorder) {
        this.tx = tx;
        this.accounts = accounts;
        this.recorder = recorder;
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

        var locked = lockInOrder(command.fromAccountId(), command.toAccountId());
        var from = locked.get(command.fromAccountId());
        var to = locked.get(command.toAccountId());

        var rejection = TransferRules.evaluate(command, from, to);
        var transfer = recorder.record(command, rejection);
        if (transfer.isCompleted()) {
            var amount = transfer.amount().amount();
            accounts.addToBalance(from.id(), -amount);
            accounts.addToBalance(to.id(), amount);
        }
        return TransferOutcome.created(transfer);
    }

    private Map<UUID, Account> lockInOrder(UUID first, UUID second) {
        var locked = new HashMap<UUID, Account>();
        Stream.of(first, second)
                .sorted(LOCK_ORDER)
                .forEach(id -> locked.put(id, accounts.lockById(id)
                        .orElseThrow(() -> new AccountNotFoundException(id))));
        return locked;
    }
}
