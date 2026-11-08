package dev.iliamuravev.ledger.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.iliamuravev.ledger.reconciliation.ReconciliationMonitor;
import dev.iliamuravev.ledger.support.IntegrationTest;
import dev.iliamuravev.ledger.support.LedgerApi;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

@AutoConfigureObservability(tracing = false)
class MetricsIT extends IntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Autowired
    ReconciliationMonitor monitor;

    LedgerApi api;

    @BeforeEach
    void setUp() {
        api = new LedgerApi(http);
    }

    @Test
    void exposesTransferOutcomesOutboxProjectionAndReconciliationMetrics() {
        var from = api.fundedAccount("EUR", 1_000);
        var to = api.openAccountId("EUR");
        api.transfer(UUID.randomUUID().toString(), from, to, 100, "EUR");
        api.transfer(UUID.randomUUID().toString(), from, to, 100_000, "EUR");
        await().atMost(Duration.ofSeconds(20))
                .until(() -> api.get("/accounts/{id}/statement", to).getBody().get("items").size() == 1);
        monitor.run();

        var metrics = http.getForEntity("/actuator/prometheus", String.class);

        assertThat(metrics.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(metrics.getBody())
                .containsPattern("ledger_transfers_seconds_count\\{[^}]*outcome=\"completed\"[^}]*type=\"transfer\"")
                .containsPattern("ledger_transfers_seconds_count\\{[^}]*outcome=\"rejected\"[^}]*"
                        + "reason=\"insufficient_funds\"")
                .containsPattern("ledger_transfers_seconds_bucket\\{")
                .contains("ledger_outbox_pending{")
                .contains("ledger_outbox_oldest_age_seconds{")
                .contains("ledger_outbox_published_total{")
                .contains("ledger_statement_projection_lag_seconds_count{")
                .containsPattern("ledger_statement_events_total\\{[^}]*result=\"applied\"")
                .containsPattern("ledger_reconciliation_consistent\\{[^}]*} 1\\.0")
                .contains("ledger_transfers_optimistic_conflicts_total{")
                .contains("hikaricp_connections_active{");
    }
}
