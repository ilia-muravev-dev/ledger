package dev.iliamuravev.ledger.ledger;

import dev.iliamuravev.ledger.domain.LedgerEntry;
import java.util.List;
import java.util.Optional;

record EntryPage(List<LedgerEntry> entries, Optional<EntryCursor> next) {
}
