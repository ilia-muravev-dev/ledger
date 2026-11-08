package dev.iliamuravev.ledger.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TransferEngineConfigTest {

    private final PessimisticTransferEngine pessimistic = mock(PessimisticTransferEngine.class);
    private final OptimisticTransferEngine optimistic = mock(OptimisticTransferEngine.class);
    private final TransferEngineConfig config = new TransferEngineConfig();

    @Test
    void selectsTheConfiguredStrategy() {
        assertThat(config.transferEngine(properties(LockingStrategy.PESSIMISTIC), pessimistic, optimistic))
                .isSameAs(pessimistic);
        assertThat(config.transferEngine(properties(LockingStrategy.OPTIMISTIC), pessimistic, optimistic))
                .isSameAs(optimistic);
    }

    private static TransferProperties properties(LockingStrategy strategy) {
        return new TransferProperties(strategy,
                new TransferProperties.Optimistic(5, Duration.ofMillis(1), Duration.ofMillis(10)));
    }
}
