package dev.iliamuravev.ledger.statement;

import dev.iliamuravev.ledger.config.LedgerClock;
import dev.iliamuravev.ledger.events.TransferRecordedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatementProjection {

    public static final String CONSUMER = "statement-projection";

    private final StatementRepository statements;
    private final LedgerClock clock;

    StatementProjection(StatementRepository statements, LedgerClock clock) {
        this.statements = statements;
        this.clock = clock;
    }

    @Transactional
    public boolean apply(TransferRecordedEvent event) {
        var now = clock.now();
        if (!statements.markProcessed(CONSUMER, event.eventId(), now)) {
            return false;
        }
        var transfer = event.transfer();
        for (var entry : event.entries()) {
            if (statements.insertEntry(entry, transfer.id(), transfer.type(), transfer.createdAt())) {
                statements.applyToBalance(entry, now);
            }
        }
        return true;
    }
}
