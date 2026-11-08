package dev.iliamuravev.ledger.transfer;

import dev.iliamuravev.ledger.config.LedgerClock;
import dev.iliamuravev.ledger.domain.FxConversion;
import dev.iliamuravev.ledger.domain.IdempotencyKeyReusedException;
import dev.iliamuravev.ledger.domain.Posting;
import dev.iliamuravev.ledger.domain.RejectionReason;
import dev.iliamuravev.ledger.domain.Transfer;
import dev.iliamuravev.ledger.domain.TransferCommand;
import dev.iliamuravev.ledger.events.TransferEventWriter;
import dev.iliamuravev.ledger.ledger.LedgerEntryRepository;
import java.util.List;
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
    public Transfer record(TransferCommand command, FxConversion fx, List<Posting> postings,
            Map<UUID, Long> balancesAfter, Optional<RejectionReason> rejection) {
        var id = UUID.randomUUID();
        var now = clock.now();
        var transfer = rejection
                .map(reason -> Transfer.rejected(id, command, reason, fx, now))
                .orElseGet(() -> Transfer.completed(id, command, fx, now));
        if (transfer.isCompleted() == postings.isEmpty()) {
            throw new IllegalArgumentException("Completed transfers need postings, rejected ones must have none");
        }
        transfers.insert(transfer);
        var entryIds = entries.insertPostings(transfer.id(), postings, now);
        idempotency.complete(command.idempotencyKey(), transfer.id());
        events.transferRecorded(transfer, postings, entryIds, balancesAfter);
        return transfer;
    }
}
