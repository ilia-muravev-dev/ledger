package dev.iliamuravev.ledger.support;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntFunction;

public final class Concurrently {

    private Concurrently() {
    }

    public static <T> List<Result<T>> run(int tasks, Duration timeout, IntFunction<Callable<T>> taskFactory)
            throws InterruptedException {
        var startGate = new CountDownLatch(1);
        var ready = new CountDownLatch(tasks);
        try (var executor = Executors.newFixedThreadPool(tasks)) {
            var futures = new ArrayList<Future<T>>(tasks);
            for (int i = 0; i < tasks; i++) {
                var task = taskFactory.apply(i);
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    startGate.await();
                    return task.call();
                }));
            }
            ready.await();
            startGate.countDown();

            var deadline = System.nanoTime() + timeout.toNanos();
            var results = new ArrayList<Result<T>>(tasks);
            for (var future : futures) {
                results.add(await(future, deadline));
            }
            return results;
        }
    }

    private static <T> Result<T> await(Future<T> future, long deadlineNanos) throws InterruptedException {
        try {
            return Result.success(future.get(Math.max(0, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS));
        } catch (ExecutionException e) {
            return Result.failure(e.getCause());
        } catch (TimeoutException e) {
            future.cancel(true);
            return Result.failure(e);
        }
    }

    public record Result<T>(T value, Throwable error) {

        static <T> Result<T> success(T value) {
            return new Result<>(value, null);
        }

        static <T> Result<T> failure(Throwable error) {
            return new Result<>(null, error);
        }

        public boolean failed() {
            return error != null;
        }
    }
}
