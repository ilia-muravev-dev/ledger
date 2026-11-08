package dev.iliamuravev.ledger.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.iliamuravev.ledger.account.AccountService;
import dev.iliamuravev.ledger.domain.Money;
import dev.iliamuravev.ledger.domain.RejectionReason;
import dev.iliamuravev.ledger.domain.TransferCommand;
import dev.iliamuravev.ledger.domain.TransferStatus;
import dev.iliamuravev.ledger.domain.TransferType;
import dev.iliamuravev.ledger.reconciliation.ReconciliationService;
import dev.iliamuravev.ledger.support.Concurrently;
import dev.iliamuravev.ledger.support.Concurrently.Result;
import dev.iliamuravev.ledger.support.IntegrationTest;
import dev.iliamuravev.ledger.support.LedgerQueries;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

abstract class TransferConcurrencyContract extends IntegrationTest {

    static final Duration TIMEOUT = Duration.ofSeconds(60);

    @Autowired
    AccountService accounts;

    @Autowired
    TransferService transfers;

    @Autowired
    LedgerQueries queries;

    @Autowired
    ReconciliationService reconciliation;

    abstract TransferEngine engine();

    @Test
    void hundredThreadsDrainingOneAccountNeverOverdraw() throws Exception {
        var source = fundedAccount(5_000);
        var destinations = IntStream.range(0, 100).mapToObj(i -> openAccount()).toList();

        var results = Concurrently.run(100, TIMEOUT,
                i -> () -> engine().execute(transfer(source, destinations.get(i), 100)));

        assertNoFailures(results);
        var statuses = results.stream().map(r -> r.value().transfer().status()).toList();
        assertThat(statuses).filteredOn(TransferStatus.COMPLETED::equals).hasSize(50);
        assertThat(results).filteredOn(r -> r.value().transfer().status() == TransferStatus.REJECTED)
                .hasSize(50)
                .allSatisfy(r -> assertThat(r.value().transfer().reason())
                        .isEqualTo(RejectionReason.INSUFFICIENT_FUNDS));

        assertThat(balance(source)).isZero();
        var received = destinations.stream().mapToLong(this::balance).sum();
        assertThat(received).isEqualTo(5_000);
        assertCachedBalanceMatchesEntries(source);
    }

    @Test
    void opposingTransfersDoNotDeadlock() throws Exception {
        var a = fundedAccount(10_000);
        var b = fundedAccount(10_000);

        var results = Concurrently.run(40, TIMEOUT, i -> () -> {
            var forward = i % 2 == 0;
            var outcomes = new java.util.ArrayList<TransferOutcome>();
            for (int n = 0; n < 10; n++) {
                outcomes.add(engine().execute(forward ? transfer(a, b, 7) : transfer(b, a, 7)));
            }
            return outcomes;
        });

        assertNoFailures(results);
        assertThat(results).flatMap(Result::value)
                .hasSize(400)
                .allSatisfy(outcome -> assertThat(outcome.transfer().status()).isEqualTo(TransferStatus.COMPLETED));
        assertThat(balance(a)).isEqualTo(10_000);
        assertThat(balance(b)).isEqualTo(10_000);
        assertCachedBalanceMatchesEntries(a);
        assertCachedBalanceMatchesEntries(b);
    }

    @Test
    void sameIdempotencyKeyFromTwentyThreadsMovesMoneyOnce() throws Exception {
        var from = fundedAccount(1_000);
        var to = openAccount();
        var command = transfer(from, to, 250);

        var results = Concurrently.run(20, TIMEOUT, i -> () -> engine().execute(command));

        assertNoFailures(results);
        var ids = results.stream().map(r -> r.value().transfer().id()).distinct().toList();
        assertThat(ids).hasSize(1);
        assertThat(results).filteredOn(r -> !r.value().replayed()).hasSize(1);
        assertThat(queries.transfersWithKey(command.idempotencyKey())).isOne();
        assertThat(balance(from)).isEqualTo(750);
        assertThat(balance(to)).isEqualTo(250);
    }

    @Test
    void randomWorkloadConservesMoneyAndReconciles() throws Exception {
        var pool = IntStream.range(0, 8).mapToObj(i -> fundedAccount(1_000)).toList();

        var results = Concurrently.run(32, TIMEOUT, i -> () -> {
            var random = ThreadLocalRandom.current();
            for (int n = 0; n < 25; n++) {
                var from = pool.get(random.nextInt(pool.size()));
                var to = pool.get(random.nextInt(pool.size()));
                if (!from.equals(to)) {
                    engine().execute(transfer(from, to, 1 + random.nextInt(300)));
                }
            }
            return null;
        });

        assertNoFailures(results);
        assertThat(pool.stream().mapToLong(this::balance).sum()).isEqualTo(8_000);
        assertThat(pool).allSatisfy(id -> assertThat(balance(id)).isNotNegative());
        pool.forEach(this::assertCachedBalanceMatchesEntries);
        assertThat(reconciliation.run().consistent()).isTrue();
    }

    private UUID openAccount() {
        return accounts.open("concurrency", "EUR").id();
    }

    private UUID fundedAccount(long amount) {
        var id = openAccount();
        transfers.deposit(UUID.randomUUID().toString(), id, Money.of(amount, "EUR"));
        return id;
    }

    private long balance(UUID accountId) {
        return accounts.get(accountId).balance().amount();
    }

    private void assertCachedBalanceMatchesEntries(UUID accountId) {
        assertThat(queries.cachedBalance(accountId)).isEqualTo(queries.sumOfEntries(accountId));
    }

    private static TransferCommand transfer(UUID from, UUID to, long amount) {
        return new TransferCommand(UUID.randomUUID().toString(), TransferType.TRANSFER, from, to,
                Money.of(amount, "EUR"));
    }

    private static <T> void assertNoFailures(List<Result<T>> results) {
        assertThat(results)
                .as("tasks that threw")
                .filteredOn(Result::failed)
                .extracting(Result::error)
                .isEmpty();
    }
}
