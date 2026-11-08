package dev.iliamuravev.ledger.transfer;

import dev.iliamuravev.ledger.domain.LedgerException;
import dev.iliamuravev.ledger.domain.Transfer;

public class TransferRejectedException extends LedgerException {

    private final transient Transfer transfer;

    public TransferRejectedException(Transfer transfer) {
        super("Transfer " + transfer.id() + " was rejected: " + transfer.reason());
        this.transfer = transfer;
    }

    public Transfer transfer() {
        return transfer;
    }
}
