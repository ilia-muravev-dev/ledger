package dev.iliamuravev.ledger.api;

import java.net.URI;
import org.springframework.http.HttpStatus;

public enum ProblemType {
    VALIDATION_FAILED("validation-failed", "Request is invalid", HttpStatus.BAD_REQUEST),
    ACCOUNT_NOT_FOUND("account-not-found", "Account not found", HttpStatus.NOT_FOUND);

    private final URI type;
    private final String title;
    private final HttpStatus status;

    ProblemType(String slug, String title, HttpStatus status) {
        this.type = URI.create("/problems/" + slug);
        this.title = title;
        this.status = status;
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
