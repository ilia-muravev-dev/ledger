package dev.iliamuravev.ledger.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Currency;
import java.util.HexFormat;
import java.util.UUID;

public record TransferCommand(
        String idempotencyKey,
        TransferType type,
        UUID fromAccountId,
        UUID toAccountId,
        Money amount,
        Currency targetCurrency) {

    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;

    public TransferCommand(String idempotencyKey, TransferType type, UUID fromAccountId, UUID toAccountId,
            Money amount) {
        this(idempotencyKey, type, fromAccountId, toAccountId, amount, null);
    }

    public TransferCommand {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidRequestException("Idempotency key must not be blank");
        }
        if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new InvalidRequestException(
                    "Idempotency key must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
        if (type == null) {
            throw new InvalidRequestException("Transfer type is required");
        }
        if (fromAccountId == null || toAccountId == null) {
            throw new InvalidRequestException("Both accounts are required");
        }
        if (fromAccountId.equals(toAccountId)) {
            throw new InvalidRequestException("Cannot transfer to the same account");
        }
        if (amount == null || !amount.isPositive()) {
            throw new InvalidRequestException("Amount must be greater than zero");
        }
        if (targetCurrency != null && targetCurrency.equals(amount.currency())) {
            targetCurrency = null;
        }
        if (targetCurrency != null && type != TransferType.TRANSFER) {
            throw new InvalidRequestException("Only transfers can convert currency");
        }
    }

    public boolean isFx() {
        return targetCurrency != null;
    }

    public Currency creditCurrency() {
        return isFx() ? targetCurrency : amount.currency();
    }

    public String fingerprint() {
        var canonical = String.join("|",
                type.name(),
                fromAccountId.toString(),
                toAccountId.toString(),
                Long.toString(amount.amount()),
                amount.currencyCode());
        if (isFx()) {
            canonical = canonical + "|" + targetCurrency.getCurrencyCode();
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
