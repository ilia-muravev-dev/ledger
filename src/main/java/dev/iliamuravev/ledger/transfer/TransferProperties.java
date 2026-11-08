package dev.iliamuravev.ledger.transfer;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("ledger.transfers")
public record TransferProperties(
        @DefaultValue("pessimistic") LockingStrategy locking,
        @DefaultValue Optimistic optimistic) {

    public record Optimistic(
            @DefaultValue("30") int maxAttempts,
            @DefaultValue("2ms") Duration initialBackoff,
            @DefaultValue("100ms") Duration maxBackoff) {

        public RetryPolicy retryPolicy() {
            return new RetryPolicy(maxAttempts, initialBackoff, maxBackoff);
        }
    }
}
