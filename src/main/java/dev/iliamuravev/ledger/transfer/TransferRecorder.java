package dev.iliamuravev.ledger.transfer;

import dev.iliamuravev.ledger.config.LedgerClock;
import dev.iliamuravev.ledger.domain.Account;
import dev.iliamuravev.ledger.domain.IdempotencyKeyReusedException;
import dev.iliamuravev.ledger.domain.RejectionReason;
import dev.iliamuravev.ledger.domain.Transfer;
import dev.iliamuravev.ledger.domain.TransferCommand;
import dev.iliamuravev.ledger.events.TransferEventWriter;
import dev.iliamuravev.ledger.ledger.LedgerEntryRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class TransferRecorder {

    private final IdempotencyRepository idempotency;
    private final TransferRepository transfers;
    private final LedgerEntryRepository entries;
    private final TransferEventWriter events;
    private final LedgerClock clock;

    TransferRecorder(IdempotencyRepository idempotency, TransferRepository transfers, LedgerEntryRepository entries,
            TransferEventWriter events, LedgerClock clock) {
        this.idempotency = idempotency;
        this.transfers = transfers;
        this.entries = entries;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<TransferOutcome> claimOrReplay(TransferCommand command) {
        var fingerprint = command.fingerprint();
        if (idempotency.claim(command.idempotencyKey(), fingerprint, clock.now())) {
            return Optional.empty();
        }
        var stored = idempotency.find(command.idempotencyKey())
                .orElseThrow(() -> new IllegalStateException("Idempotency record vanished after a conflict"));
        if (!stored.requestHash().equals(fingerprint)) {
            throw new IdempotencyKeyReusedException(command.idempotencyKey());
        }
        var transfer = transfers.findById(stored.transferId())
                .orElseThrow(() -> new IllegalStateException("Idempotency record points to a missing transfer"));
        return Optional.of(TransferOutcome.replayed(transfer));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Transfer record(TransferCommand command, Account from, Account to, Optional<RejectionReason> rejection) {
        var id = UUID.randomUUID();
        var now = clock.now();
        var transfer = rejection
                .map(reason -> Transfer.rejected(id, command, reason, now))
                .orElseGet(() -> Transfer.completed(id, command, now));
        transfers.insert(transfer);
        var entryIds = transfer.isCompleted() ? entries.insertDoubleEntry(transfer) : Map.<UUID, Long>of();
        idempotency.complete(command.idempotencyKey(), transfer.id());
        events.transferRecorded(transfer, from, to, entryIds);
        return transfer;
    }
}
