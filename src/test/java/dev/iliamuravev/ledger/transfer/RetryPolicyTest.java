package dev.iliamuravev.ledger.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy(10, Duration.ofMillis(2), Duration.ofMillis(50));

    @Test
    void ceilingGrowsExponentiallyUpToTheMaximum() {
        assertThat(policy.backoffCeiling(1)).isEqualTo(Duration.ofMillis(2));
        assertThat(policy.backoffCeiling(2)).isEqualTo(Duration.ofMillis(4));
        assertThat(policy.backoffCeiling(4)).isEqualTo(Duration.ofMillis(16));
        assertThat(policy.backoffCeiling(5)).isEqualTo(Duration.ofMillis(32));
        assertThat(policy.backoffCeiling(6)).isEqualTo(Duration.ofMillis(50));
        assertThat(policy.backoffCeiling(1_000)).isEqualTo(Duration.ofMillis(50));
    }

    @RepeatedTest(50)
    void jitteredBackoffStaysWithinTheCeiling() {
        assertThat(policy.backoff(3)).isBetween(Duration.ZERO, Duration.ofMillis(8));
    }

    @Test
    void zeroBackoffIsAllowed() {
        var noWait = new RetryPolicy(3, Duration.ZERO, Duration.ZERO);

        assertThat(noWait.backoff(2)).isZero();
    }

    @Test
    void rejectsNonsenseConfiguration() {
        assertThatThrownBy(() -> new RetryPolicy(0, Duration.ZERO, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(1, Duration.ofMillis(10), Duration.ofMillis(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
