package dev.iliamuravev.ledger.api;

import dev.iliamuravev.ledger.domain.AccountNotEmptyException;
import dev.iliamuravev.ledger.domain.AccountNotFoundException;
import dev.iliamuravev.ledger.domain.IdempotencyKeyReusedException;
import dev.iliamuravev.ledger.domain.InvalidRequestException;
import dev.iliamuravev.ledger.domain.TransferNotFoundException;
import dev.iliamuravev.ledger.transfer.TransferRejectedException;
import java.util.LinkedHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class ProblemDetailsHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalidRequest(InvalidRequestException e) {
        return problem(ProblemType.VALIDATION_FAILED, e.getMessage());
    }

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail accountNotFound(AccountNotFoundException e) {
        var problem = problem(ProblemType.ACCOUNT_NOT_FOUND, e.getMessage());
        problem.setProperty("accountId", e.accountId());
        return problem;
    }

    @ExceptionHandler(TransferNotFoundException.class)
    ProblemDetail transferNotFound(TransferNotFoundException e) {
        var problem = problem(ProblemType.TRANSFER_NOT_FOUND, e.getMessage());
        problem.setProperty("transferId", e.transferId());
        return problem;
    }

    @ExceptionHandler(AccountNotEmptyException.class)
    ProblemDetail accountNotEmpty(AccountNotEmptyException e) {
        var problem = problem(ProblemType.ACCOUNT_NOT_EMPTY, e.getMessage());
        problem.setProperty("accountId", e.accountId());
        return problem;
    }

    @ExceptionHandler(TransferRejectedException.class)
    ProblemDetail transferRejected(TransferRejectedException e) {
        var transfer = e.transfer();
        var problem = problem(ProblemType.of(transfer.reason()), e.getMessage());
        problem.setProperty("transferId", transfer.id());
        problem.setProperty("reason", transfer.reason().name());
        return problem;
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ProblemDetail idempotencyKeyReused(IdempotencyKeyReusedException e) {
        var problem = problem(ProblemType.IDEMPOTENCY_KEY_REUSED, e.getMessage());
        problem.setProperty("idempotencyKey", e.idempotencyKey());
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException e,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        var errors = new LinkedHashMap<String, String>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        var problem = problem(ProblemType.VALIDATION_FAILED, "One or more fields are invalid");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    static ProblemDetail problem(ProblemType type, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(type.status(), detail);
        problem.setType(type.type());
        problem.setTitle(type.title());
        return problem;
    }
}
