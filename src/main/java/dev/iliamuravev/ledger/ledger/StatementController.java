package dev.iliamuravev.ledger.ledger;

import dev.iliamuravev.ledger.domain.LedgerEntry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class StatementController {

    private final StatementService statements;

    StatementController(StatementService statements) {
        this.statements = statements;
    }

    @GetMapping("/accounts/{accountId}/entries")
    EntryPageResponse entries(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "" + StatementService.DEFAULT_LIMIT) int limit,
            @RequestParam Optional<String> cursor) {
        var page = statements.entries(accountId, cursor.map(EntryCursor::decode), limit);
        return new EntryPageResponse(
                page.entries().stream().map(EntryResponse::from).toList(),
                page.next().map(EntryCursor::encode).orElse(null));
    }

    record EntryPageResponse(List<EntryResponse> items, String nextCursor) {
    }

    record EntryResponse(long id, UUID transferId, long amount, String currency, Instant createdAt) {

        static EntryResponse from(LedgerEntry entry) {
            return new EntryResponse(entry.id(), entry.transferId(), entry.amount().amount(),
                    entry.amount().currencyCode(), entry.createdAt());
        }
    }
}
