package dev.iliamuravev.ledger.statement;

import dev.iliamuravev.ledger.account.AccountService;
import dev.iliamuravev.ledger.domain.InvalidRequestException;
import dev.iliamuravev.ledger.ledger.EntryCursor;
import dev.iliamuravev.ledger.statement.StatementRepository.StatementLine;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ProjectedStatementController {

    static final int MAX_LIMIT = 200;

    private final AccountService accounts;
    private final StatementRepository statements;

    ProjectedStatementController(AccountService accounts, StatementRepository statements) {
        this.accounts = accounts;
        this.statements = statements;
    }

    @GetMapping("/accounts/{accountId}/statement")
    @Transactional(readOnly = true)
    StatementResponse statement(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam Optional<String> cursor) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequestException("limit must be between 1 and " + MAX_LIMIT);
        }
        var account = accounts.get(accountId);
        var before = cursor.map(EntryCursor::decode).map(EntryCursor::beforeEntryId);
        var rows = statements.findPage(accountId, before, limit + 1);
        var page = rows.size() > limit ? rows.subList(0, limit) : rows;
        var next = rows.size() > limit ? new EntryCursor(page.getLast().entryId()).encode() : null;
        var view = statements.findBalance(accountId);
        return new StatementResponse(
                accountId,
                account.currency().getCurrencyCode(),
                view.map(StatementRepository.BalanceView::balance).orElse(0L),
                view.map(StatementRepository.BalanceView::lastEntryId).orElse(null),
                view.map(StatementRepository.BalanceView::updatedAt).orElse(null),
                page,
                next);
    }

    record StatementResponse(
            UUID accountId,
            String currency,
            long balance,
            Long lastEntryId,
            Instant projectedAt,
            List<StatementLine> items,
            String nextCursor) {
    }
}
