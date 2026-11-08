package dev.iliamuravev.ledger.domain;

public class InvalidRequestException extends LedgerException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
