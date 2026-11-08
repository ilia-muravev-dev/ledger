package dev.iliamuravev.ledger.transfer;

import dev.iliamuravev.ledger.domain.Transfer;

public record TransferOutcome(Transfer transfer, boolean replayed) {

    public static TransferOutcome created(Transfer transfer) {
        return new TransferOutcome(transfer, false);
    }

    public static TransferOutcome replayed(Transfer transfer) {
        return new TransferOutcome(transfer, true);
    }
}
