package dev.iliamuravev.ledger.reconciliation;

import dev.iliamuravev.ledger.domain.TransferStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReconciliationReport(
        Instant checkedAt,
        boolean consistent,
        int accountsChecked,
        int transfersChecked,
        List<BalanceMismatch> balanceMismatches,
        List<UnbalancedCurrency> unbalancedCurrencies,
        List<MalformedTransfer> malformedTransfers) {

    public ReconciliationReport {
        balanceMismatches = List.copyOf(balanceMismatches);
        unbalancedCurrencies = List.copyOf(unbalancedCurrencies);
        malformedTransfers = List.copyOf(malformedTransfers);
    }

    public record BalanceMismatch(UUID accountId, long cachedBalance, BigDecimal sumOfEntries) {
    }

    public record UnbalancedCurrency(String currency, BigDecimal sumOfEntries) {
    }

    public record MalformedTransfer(UUID transferId, TransferStatus status, int entryCount, BigDecimal sumOfEntries) {
    }
}
