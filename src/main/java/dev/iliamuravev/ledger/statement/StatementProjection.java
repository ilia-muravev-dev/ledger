package dev.iliamuravev.ledger.statement;

import dev.iliamuravev.ledger.config.LedgerClock;
import dev.iliamuravev.ledger.events.TransferRecordedEvent;
import dev.iliamuravev.ledger.statement.StatementRepository.BalanceDelta;
import dev.iliamuravev.ledger.statement.StatementRepository.NewLine;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
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
        return applyAll(List.of(event)).getFirst();
    }

    @Transactional
    public List<Boolean> applyAll(List<TransferRecordedEvent> events) {
        var now = clock.now();
        var unique = events.stream()
                .collect(Collectors.toMap(TransferRecordedEvent::eventId, e -> e, (a, b) -> a, LinkedHashMap::new));
        var fresh = statements.markProcessed(CONSUMER, unique.keySet(), now);

        var lines = unique.values().stream()
                .filter(event -> fresh.contains(event.eventId()))
                .flatMap(event -> event.entries().stream().map(entry -> new NewLine(entry, event.transfer().id(),
                        event.transfer().type(), event.transfer().createdAt())))
                .toList();
        var inserted = statements.insertLines(lines);
        statements.applyToBalances(deltas(inserted), now);

        var claimed = new HashSet<UUID>();
        return events.stream().map(event -> fresh.contains(event.eventId()) && claimed.add(event.eventId())).toList();
    }

    private static List<BalanceDelta> deltas(List<NewLine> lines) {
        return lines.stream()
                .collect(Collectors.groupingBy(line -> line.entry().accountId()))
                .entrySet().stream()
                .map(group -> {
                    var latest = group.getValue().stream()
                            .max(Comparator.comparingLong(line -> line.entry().entryId()))
                            .orElseThrow()
                            .entry();
                    return new BalanceDelta(group.getKey(), latest.currency(), latest.entryId(),
                            latest.balanceAfter(), group.getValue().size());
                })
                .toList();
    }
}
