package dev.iliamuravev.ledger.events;

import dev.iliamuravev.ledger.config.LedgerClock;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
class OutboxMetrics implements MeterBinder {

    private final OutboxRepository outbox;
    private final LedgerClock clock;

    OutboxMetrics(OutboxRepository outbox, LedgerClock clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("ledger.outbox.pending", outbox, OutboxMetrics::pending)
                .description("Outbox events not yet published to Kafka")
                .register(registry);
        Gauge.builder("ledger.outbox.oldest.age", this, OutboxMetrics::oldestAgeSeconds)
                .description("Age of the oldest unpublished outbox event")
                .baseUnit("seconds")
                .register(registry);
    }

    private static double pending(OutboxRepository outbox) {
        try {
            return outbox.countUnpublished();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    private double oldestAgeSeconds() {
        try {
            return outbox.oldestUnpublishedAt()
                    .map(createdAt -> Duration.between(createdAt, clock.now()).toMillis() / 1000.0)
                    .orElse(0.0);
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }
}
