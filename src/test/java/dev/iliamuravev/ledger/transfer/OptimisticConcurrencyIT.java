package dev.iliamuravev.ledger.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.iliamuravev.ledger.account.AccountRepository;
import dev.iliamuravev.ledger.domain.Money;
import dev.iliamuravev.ledger.domain.TransferCommand;
import dev.iliamuravev.ledger.domain.TransferType;
import dev.iliamuravev.ledger.support.Concurrently;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class OptimisticConcurrencyIT extends TransferConcurrencyContract {

    @Autowired
    TransactionTemplate tx;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    TransferRecorder recorder;

    @Autowired
    FxRouter fxRouter;

    SimpleMeterRegistry meters;

    OptimisticTransferEngine engine;

    @BeforeEach
    void setUpEngine() {
        meters = new SimpleMeterRegistry();
        engine = engineWith(new RetryPolicy(500, Duration.ofMillis(1), Duration.ofMillis(20)));
    }

    @Override
    TransferEngine engine() {
        return engine;
    }

    @Test
    void conflictsAreRetriedAndCounted() throws Exception {
        var a = fundedEur(10_000);
        var b = fundedEur(10_000);

        var results = Concurrently.run(16, TIMEOUT, i -> () -> engine.execute(command(i % 2 == 0 ? a : b,
                i % 2 == 0 ? b : a, 1)));

        assertThat(results).noneMatch(Concurrently.Result::failed);
        assertThat(meters.counter("ledger.transfers.optimistic.conflicts").count()).isPositive();
    }

    @Test
    void givesUpAfterMaxAttemptsWithoutLeavingATrace() throws Exception {
        var impatient = engineWith(new RetryPolicy(1, Duration.ZERO, Duration.ZERO));
        var a = fundedEur(10_000);
        var b = fundedEur(10_000);
        var commands = IntStream.range(0, 30).mapToObj(i -> command(a, b, 1)).toList();

        var results = Concurrently.run(30, TIMEOUT, i -> () -> impatient.execute(commands.get(i)));

        var failed = results.stream().filter(Concurrently.Result::failed).toList();
        assertThat(failed).isNotEmpty()
                .allSatisfy(r -> assertThat(r.error()).isInstanceOf(TransferContentionException.class));
        var completed = results.size() - failed.size();
        assertThat(accounts.get(b).balance().amount()).isEqualTo(10_000 + completed);
        for (int i = 0; i < results.size(); i++) {
            var expected = results.get(i).failed() ? 0 : 1;
            assertThat(queries.transfersWithKey(commands.get(i).idempotencyKey())).isEqualTo(expected);
        }
    }

    private OptimisticTransferEngine engineWith(RetryPolicy policy) {
        return new OptimisticTransferEngine(tx, accountRepository, recorder, fxRouter, policy, meters);
    }

    private UUID fundedEur(long amount) {
        var id = accounts.open("optimistic", "EUR").id();
        transfers.deposit(UUID.randomUUID().toString(), id, Money.of(amount, "EUR"));
        return id;
    }

    private static TransferCommand command(UUID from, UUID to, long amount) {
        return new TransferCommand(UUID.randomUUID().toString(), TransferType.TRANSFER, from, to,
                Money.of(amount, "EUR"));
    }
}
