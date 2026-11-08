package dev.iliamuravev.ledger.ledger;

import dev.iliamuravev.ledger.account.AccountService;
import dev.iliamuravev.ledger.domain.InvalidRequestException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class StatementService {

    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 200;

    private final AccountService accounts;
    private final LedgerEntryRepository entries;

    StatementService(AccountService accounts, LedgerEntryRepository entries) {
        this.accounts = accounts;
        this.entries = entries;
    }

    @Transactional(readOnly = true)
    EntryPage entries(UUID accountId, Optional<EntryCursor> cursor, int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequestException("limit must be between 1 and " + MAX_LIMIT);
        }
        var currency = accounts.get(accountId).currency();
        var rows = entries.findPage(accountId, currency, cursor.map(EntryCursor::beforeEntryId), limit + 1);
        if (rows.size() <= limit) {
            return new EntryPage(rows, Optional.empty());
        }
        var page = rows.subList(0, limit);
        return new EntryPage(page, Optional.of(new EntryCursor(page.getLast().id())));
    }
}
