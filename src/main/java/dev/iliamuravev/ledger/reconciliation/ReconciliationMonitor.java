package dev.iliamuravev.ledger.reconciliation;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ledger.reconciliation.monitor", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class ReconciliationMonitor {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationMonitor.class);

    private final ReconciliationService reconciliation;
    private final AtomicReference<ReconciliationReport> last = new AtomicReference<>();

    ReconciliationMonitor(ReconciliationService reconciliation, MeterRegistry meters) {
        this.reconciliation = reconciliation;
        Gauge.builder("ledger.reconciliation.consistent", last,
                        ref -> ref.get() == null ? Double.NaN : ref.get().consistent() ? 1 : 0)
                .description("1 if the last reconciliation found the books consistent, 0 if not")
                .register(meters);
        Gauge.builder("ledger.reconciliation.discrepancies", last,
                        ref -> ref.get() == null ? Double.NaN : ref.get().balanceMismatches().size()
                                + ref.get().unbalancedCurrencies().size() + ref.get().malformedTransfers().size())
                .description("Discrepancies found by the last reconciliation")
                .register(meters);
    }

    @Scheduled(initialDelayString = "${ledger.reconciliation.monitor.initial-delay-ms:30000}",
            fixedDelayString = "${ledger.reconciliation.monitor.interval-ms:300000}")
    public ReconciliationReport run() {
        var report = reconciliation.run();
        last.set(report);
        if (!report.consistent()) {
            log.error("Reconciliation found discrepancies: {} balance mismatches, {} unbalanced currencies, "
                            + "{} malformed transfers", report.balanceMismatches().size(),
                    report.unbalancedCurrencies().size(), report.malformedTransfers().size());
        }
        return report;
    }
}
