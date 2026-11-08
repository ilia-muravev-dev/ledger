package dev.iliamuravev.ledger.transfer;

import dev.iliamuravev.ledger.account.AccountService;
import dev.iliamuravev.ledger.domain.Money;
import dev.iliamuravev.ledger.domain.Transfer;
import dev.iliamuravev.ledger.domain.TransferCommand;
import dev.iliamuravev.ledger.domain.TransferNotFoundException;
import dev.iliamuravev.ledger.domain.TransferType;
import java.util.Currency;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    private final TransferEngine engine;
    private final AccountService accounts;
    private final TransferRepository transfers;
    private final TransferMetrics metrics;

    public TransferService(TransferEngine engine, AccountService accounts, TransferRepository transfers,
            TransferMetrics metrics) {
        this.engine = engine;
        this.accounts = accounts;
        this.transfers = transfers;
        this.metrics = metrics;
    }

    public TransferOutcome transfer(String idempotencyKey, UUID fromAccountId, UUID toAccountId, Money amount) {
        return transfer(idempotencyKey, fromAccountId, toAccountId, amount, null);
    }

    public TransferOutcome transfer(String idempotencyKey, UUID fromAccountId, UUID toAccountId, Money amount,
            Currency targetCurrency) {
        return execute(new TransferCommand(idempotencyKey, TransferType.TRANSFER, fromAccountId, toAccountId,
                amount, targetCurrency));
    }

    public TransferOutcome deposit(String idempotencyKey, UUID accountId, Money amount) {
        var settlement = accounts.settlementAccount(amount.currency());
        return execute(new TransferCommand(idempotencyKey, TransferType.DEPOSIT, settlement.id(), accountId, amount));
    }

    private TransferOutcome execute(TransferCommand command) {
        return metrics.record(command, () -> engine.execute(command));
    }

    @Transactional(readOnly = true)
    public Transfer get(UUID id) {
        return transfers.findById(id).orElseThrow(() -> new TransferNotFoundException(id));
    }
}
