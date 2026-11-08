package dev.iliamuravev.ledger.api;

import dev.iliamuravev.ledger.domain.RejectionReason;
import java.net.URI;
import org.springframework.http.HttpStatus;

public enum ProblemType {
    VALIDATION_FAILED("validation-failed", "Request is invalid", HttpStatus.BAD_REQUEST),
    ACCOUNT_NOT_FOUND("account-not-found", "Account not found", HttpStatus.NOT_FOUND),
    TRANSFER_NOT_FOUND("transfer-not-found", "Transfer not found", HttpStatus.NOT_FOUND),
    ACCOUNT_NOT_EMPTY("account-not-empty", "Account balance is not zero", HttpStatus.CONFLICT),
    INSUFFICIENT_FUNDS("insufficient-funds", "Insufficient funds", HttpStatus.CONFLICT),
    ACCOUNT_CLOSED("account-closed", "Account is closed", HttpStatus.CONFLICT),
    CURRENCY_MISMATCH("currency-mismatch", "Currency does not match the accounts", HttpStatus.CONFLICT),
    FX_RATE_UNAVAILABLE("fx-rate-unavailable", "No FX rate for this currency pair", HttpStatus.CONFLICT),
    SETTLEMENT_ACCOUNT_NOT_ALLOWED("settlement-account-not-allowed",
            "Settlement accounts cannot be used here", HttpStatus.CONFLICT),
    IDEMPOTENCY_KEY_REUSED("idempotency-key-reused",
            "Idempotency key was used with a different request", HttpStatus.UNPROCESSABLE_ENTITY),
    CONTENTION("contention", "Too much concurrent activity on the accounts", HttpStatus.SERVICE_UNAVAILABLE);

    private final URI type;
    private final String title;
    private final HttpStatus status;

    ProblemType(String slug, String title, HttpStatus status) {
        this.type = URI.create("/problems/" + slug);
        this.title = title;
        this.status = status;
    }

    public static ProblemType of(RejectionReason reason) {
        return switch (reason) {
            case INSUFFICIENT_FUNDS -> INSUFFICIENT_FUNDS;
            case ACCOUNT_CLOSED -> ACCOUNT_CLOSED;
            case CURRENCY_MISMATCH -> CURRENCY_MISMATCH;
            case SETTLEMENT_ACCOUNT_NOT_ALLOWED -> SETTLEMENT_ACCOUNT_NOT_ALLOWED;
            case FX_RATE_UNAVAILABLE -> FX_RATE_UNAVAILABLE;
        };
    }

    public URI type() {
        return type;
    }

    public String title() {
        return title;
    }

    public HttpStatus status() {
        return status;
    }
}
