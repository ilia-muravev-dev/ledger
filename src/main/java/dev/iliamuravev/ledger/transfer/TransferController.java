package dev.iliamuravev.ledger.transfer;

import dev.iliamuravev.ledger.domain.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
class TransferController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final TransferService transfers;

    TransferController(TransferService transfers) {
        this.transfers = transfers;
    }

    @PostMapping("/transfers")
    ResponseEntity<TransferResponse> transfer(
            @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        var outcome = transfers.transfer(idempotencyKey, request.fromAccountId(), request.toAccountId(),
                Money.of(request.amount(), request.currency()));
        return respond(outcome);
    }

    @PostMapping("/accounts/{accountId}/deposits")
    ResponseEntity<TransferResponse> deposit(
            @PathVariable UUID accountId,
            @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
            @Valid @RequestBody DepositRequest request) {
        var outcome = transfers.deposit(idempotencyKey, accountId, Money.of(request.amount(), request.currency()));
        return respond(outcome);
    }

    @GetMapping("/transfers/{id}")
    TransferResponse get(@PathVariable UUID id) {
        return TransferResponse.from(transfers.get(id));
    }

    private static ResponseEntity<TransferResponse> respond(TransferOutcome outcome) {
        var transfer = outcome.transfer();
        if (!transfer.isCompleted()) {
            throw new TransferRejectedException(transfer);
        }
        var body = TransferResponse.from(transfer);
        if (outcome.replayed()) {
            return ResponseEntity.ok().location(locationOf(transfer.id())).body(body);
        }
        return ResponseEntity.status(HttpStatus.CREATED).location(locationOf(transfer.id())).body(body);
    }

    private static URI locationOf(UUID transferId) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/transfers/{id}")
                .buildAndExpand(transferId)
                .toUri();
    }

    record TransferRequest(
            @NotNull UUID fromAccountId,
            @NotNull UUID toAccountId,
            @NotNull @Positive Long amount,
            @NotBlank String currency) {
    }

    record DepositRequest(
            @NotNull @Positive Long amount,
            @NotBlank String currency) {
    }
}
