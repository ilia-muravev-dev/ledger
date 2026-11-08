package dev.iliamuravev.ledger.reconciliation;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "reconciliation")
class ReconciliationEndpoint {

    private final ReconciliationService reconciliation;

    ReconciliationEndpoint(ReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @ReadOperation
    ReconciliationReport reconcile() {
        return reconciliation.run();
    }
}
