package dev.iliamuravev.ledger.domain;

import java.util.UUID;

public class TransferNotFoundException extends LedgerException {

    private final UUID transferId;

    public TransferNotFoundException(UUID transferId) {
        super("Transfer " + transferId + " does not exist");
        this.transferId = transferId;
    }

    public UUID transferId() {
        return transferId;
    }
}
