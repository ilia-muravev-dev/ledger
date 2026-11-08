package dev.iliamuravev.ledger.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.iliamuravev.ledger.account.AccountService;
import dev.iliamuravev.ledger.domain.Money;
import dev.iliamuravev.ledger.domain.TransferCommand;
import dev.iliamuravev.ledger.domain.TransferType;
import dev.iliamuravev.ledger.reconciliation.ReconciliationService;
import dev.iliamuravev.ledger.support.IntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("benchmark")
class LockingBenchmark extends IntegrationTest {

    static final int THREADS = Integer.getInteger("benchmark.threads", 32);
    static final int TRANSFERS = Integer.getInteger("benchmark.transfers", 5_000);
    static final int WARMUP = Integer.getInteger("benchmark.warmup", 500);

    @Autowired
    PessimisticTransferEngine pessimistic;

    @Autowired
    OptimisticTransferEngine optimistic;

    @Autowired
    AccountService accounts;

    @Autowired
    TransferService transfers;

    @Autowired
    ReconciliationService reconciliation;

    @Autowired
    MeterRegistry meters;

    @Test
    void compareLockingStrategies() throws Exception {
        var scenarios = List.of(
                new Scenario("Hot pair: 2 accounts, both directions", 2, 0),
                new Scenario("Hot source: 1 account pays 100 others", 101, 1),
                new Scenario("Uniform: 1,000 accounts", 1_000, 0));

        var rows = new ArrayList<String>();
        for (var scenario : scenarios) {
            for (var strategy : LockingStrategy.values()) {
                var accountIds = fund(scenario.accounts());
                var engine = strategy == LockingStrategy.PESSIMISTIC ? pessimistic : optimistic;
                run(engine, scenario, accountIds, WARMUP);
                var result = run(engine, scenario, accountIds, TRANSFERS);
                rows.add(result.toRow(scenario, strategy));
            }
        }

        assertThat(reconciliation.run().consistent()).isTrue();
        report(rows);
    }

    private Result run(TransferEngine engine, Scenario scenario, List<UUID> accountIds, int total)
            throws InterruptedException {
        var conflictsBefore = conflicts();
        var tickets = new AtomicInteger();
        var failures = new AtomicInteger();
        var latencies = new ConcurrentLinkedQueue<Long>();
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(THREADS);
        long elapsed;
        try {
            for (int t = 0; t < THREADS; t++) {
                executor.submit(() -> {
                    start.await();
                    while (tickets.getAndIncrement() < total) {
                        var command = scenario.next(accountIds);
                        var began = System.nanoTime();
                        try {
                            engine.execute(command);
                        } catch (TransferContentionException e) {
                            failures.incrementAndGet();
                        }
                        latencies.add(System.nanoTime() - began);
                    }
                    return null;
                });
            }
            var began = System.nanoTime();
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.MINUTES)).isTrue();
            elapsed = System.nanoTime() - began;
        } finally {
            executor.shutdownNow();
        }
        var sorted = latencies.stream().mapToLong(Long::longValue).sorted().toArray();
        return Result.of(total, elapsed, sorted, conflicts() - conflictsBefore, failures.get());
    }

    private List<UUID> fund(int count) {
        return IntStream.range(0, count).mapToObj(i -> {
            var id = accounts.open("benchmark", "EUR").id();
            transfers.deposit(UUID.randomUUID().toString(), id, Money.of(1_000_000_000L, "EUR"));
            return id;
        }).toList();
    }

    private double conflicts() {
        var counter = meters.find("ledger.transfers.optimistic.conflicts").counter();
        return counter == null ? 0 : counter.count();
    }

    private static void report(List<String> rows) throws IOException {
        var table = new StringBuilder()
                .append("Threads: ").append(THREADS).append(", transfers per run: ").append(TRANSFERS)
                .append(", warm-up: ").append(WARMUP).append(", Hikari pool: 20\n\n")
                .append("| Scenario | Strategy | Throughput (transfers/s) | p50 (ms) | p99 (ms) "
                        + "| Conflicts / transfer | Gave up |\n")
                .append("|---|---|---:|---:|---:|---:|---:|\n");
        rows.forEach(row -> table.append(row).append('\n'));
        var output = Path.of(System.getProperty("benchmark.output", "build/reports/benchmark/locking.md"));
        Files.createDirectories(output.getParent());
        Files.writeString(output, table);
        System.out.println(table);
    }

    record Scenario(String name, int accounts, int hotSources) {

        TransferCommand next(List<UUID> ids) {
            var random = ThreadLocalRandom.current();
            UUID from;
            UUID to;
            if (hotSources > 0) {
                from = ids.getFirst();
                to = ids.get(1 + random.nextInt(ids.size() - 1));
            } else {
                var a = random.nextInt(ids.size());
                var b = (a + 1 + random.nextInt(ids.size() - 1)) % ids.size();
                from = ids.get(a);
                to = ids.get(b);
            }
            return new TransferCommand(UUID.randomUUID().toString(), TransferType.TRANSFER, from, to,
                    Money.of(1, "EUR"));
        }
    }

    record Result(int transfers, long elapsedNanos, double p50Millis, double p99Millis, double conflicts,
            int failures) {

        static Result of(int transfers, long elapsedNanos, long[] sortedLatencies, double conflicts, int failures) {
            return new Result(transfers, elapsedNanos, percentile(sortedLatencies, 0.50),
                    percentile(sortedLatencies, 0.99), conflicts, failures);
        }

        String toRow(Scenario scenario, LockingStrategy strategy) {
            var throughput = transfers / (elapsedNanos / 1e9);
            return String.format(Locale.ROOT, "| %s | %s | %,.0f | %.1f | %.1f | %.2f | %d |",
                    scenario.name(), strategy.name().toLowerCase(Locale.ROOT), throughput,
                    p50Millis, p99Millis, conflicts / transfers, failures);
        }

        private static double percentile(long[] sorted, double p) {
            if (sorted.length == 0) {
                return 0;
            }
            var index = (int) Math.ceil(p * sorted.length) - 1;
            return sorted[Math.max(0, index)] / 1e6;
        }
    }
}
