package dev.iliamuravev.ledger.transfer;

import dev.iliamuravev.ledger.domain.AccountNotFoundException;
import dev.iliamuravev.ledger.domain.IdempotencyKeyReusedException;
import dev.iliamuravev.ledger.domain.InvalidRequestException;
import dev.iliamuravev.ledger.domain.TransferCommand;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.util.Locale;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
class TransferMetrics {

    static final String TIMER = "ledger.transfers";

    private final MeterRegistry meters;
    private final String engine;

    TransferMetrics(MeterRegistry meters, TransferProperties properties) {
        this.meters = meters;
        this.engine = properties.locking().name().toLowerCase(Locale.ROOT);
    }

    TransferOutcome record(TransferCommand command, Supplier<TransferOutcome> execution) {
        var sample = Timer.start(meters);
        var outcome = "error";
        var reason = "none";
        try {
            var result = execution.get();
            var transfer = result.transfer();
            if (result.replayed()) {
                outcome = "replayed";
            } else if (transfer.isCompleted()) {
                outcome = "completed";
            } else {
                outcome = "rejected";
                reason = transfer.reason().name().toLowerCase(Locale.ROOT);
            }
            return result;
        } catch (InvalidRequestException e) {
            outcome = "invalid";
            throw e;
        } catch (AccountNotFoundException e) {
            outcome = "not_found";
            throw e;
        } catch (IdempotencyKeyReusedException e) {
            outcome = "key_reused";
            throw e;
        } catch (TransferContentionException e) {
            outcome = "contention";
            throw e;
        } finally {
            sample.stop(Timer.builder(TIMER)
                    .description("Transfer execution time, including retries, by outcome")
                    .tags(Tags.of(
                            "engine", engine,
                            "type", command.type().name().toLowerCase(Locale.ROOT),
                            "fx", Boolean.toString(command.isFx()),
                            "outcome", outcome,
                            "reason", reason))
                    .register(meters));
        }
    }
}
