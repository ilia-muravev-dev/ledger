package dev.iliamuravev.ledger.transfer;

import org.springframework.beans.factory.annotation.Autowired;

class PessimisticConcurrencyIT extends TransferConcurrencyContract {

    @Autowired
    PessimisticTransferEngine engine;

    @Override
    TransferEngine engine() {
        return engine;
    }
}
