package dev.iliamuravev.ledger.transfer;

import dev.iliamuravev.ledger.domain.LedgerException;

public class TransferContentionException extends LedgerException {

    public TransferContentionException(int attempts) {
        super("Transfer could not be applied after " + attempts + " attempts because of concurrent updates; retry later");
    }
}
