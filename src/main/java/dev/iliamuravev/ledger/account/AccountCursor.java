package dev.iliamuravev.ledger.account;

import dev.iliamuravev.ledger.domain.InvalidRequestException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

public record AccountCursor(Instant createdAt, UUID id) {

    private static final String PREFIX = "a1:";

    public AccountCursor {
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(id, "id");
    }

    public static AccountCursor decode(String token) {
        try {
            var decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            if (!decoded.startsWith(PREFIX)) {
                throw new InvalidRequestException("Cursor is invalid");
            }
            var parts = decoded.substring(PREFIX.length()).split(":", 2);
            var micros = Long.parseLong(parts[0]);
            return new AccountCursor(Instant.EPOCH.plus(micros, ChronoUnit.MICROS), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            throw new InvalidRequestException("Cursor is invalid");
        }
    }

    public String encode() {
        var micros = ChronoUnit.MICROS.between(Instant.EPOCH, createdAt);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((PREFIX + micros + ":" + id).getBytes(StandardCharsets.UTF_8));
    }
}
