package dev.iliamuravev.ledger.transfer;

import dev.iliamuravev.ledger.account.AccountService;
import dev.iliamuravev.ledger.domain.Money;
import dev.iliamuravev.ledger.domain.Transfer;
import dev.iliamuravev.ledger.domain.TransferCommand;
import dev.iliamuravev.ledger.domain.TransferNotFoundException;
import dev.iliamuravev.ledger.domain.TransferType;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    private final TransferEngine engine;
    private final AccountService accounts;
    private final TransferRepository transfers;

    public TransferService(TransferEngine engine, AccountService accounts, TransferRepository transfers) {
        this.engine = engine;
        this.accounts = accounts;
        this.transfers = transfers;
    }

    public TransferOutcome transfer(String idempotencyKey, UUID fromAccountId, UUID toAccountId, Money amount) {
        return engine.execute(
                new TransferCommand(idempotencyKey, TransferType.TRANSFER, fromAccountId, toAccountId, amount));
    }

    public TransferOutcome deposit(String idempotencyKey, UUID accountId, Money amount) {
        var settlement = accounts.settlementAccount(amount.currency());
        return engine.execute(
                new TransferCommand(idempotencyKey, TransferType.DEPOSIT, settlement.id(), accountId, amount));
    }

    @Transactional(readOnly = true)
    public Transfer get(UUID id) {
        return transfers.findById(id).orElseThrow(() -> new TransferNotFoundException(id));
    }
}
