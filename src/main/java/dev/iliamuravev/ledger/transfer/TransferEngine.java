package dev.iliamuravev.ledger.transfer;

import dev.iliamuravev.ledger.domain.TransferCommand;

public interface TransferEngine {

    TransferOutcome execute(TransferCommand command);
}
