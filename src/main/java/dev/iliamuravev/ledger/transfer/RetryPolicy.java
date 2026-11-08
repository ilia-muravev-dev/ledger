package dev.iliamuravev.ledger.transfer;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public record RetryPolicy(int maxAttempts, Duration initialBackoff, Duration maxBackoff) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        if (initialBackoff.isNegative() || maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("Backoff must satisfy 0 <= initial <= max");
        }
    }

    Duration backoffCeiling(int failedAttempts) {
        var shift = Math.min(failedAttempts - 1, 20);
        var ceiling = initialBackoff.toNanos() << shift;
        return Duration.ofNanos(Math.min(ceiling, maxBackoff.toNanos()));
    }

    Duration backoff(int failedAttempts) {
        var ceiling = backoffCeiling(failedAttempts).toNanos();
        return ceiling == 0 ? Duration.ZERO : Duration.ofNanos(ThreadLocalRandom.current().nextLong(ceiling + 1));
    }
}
