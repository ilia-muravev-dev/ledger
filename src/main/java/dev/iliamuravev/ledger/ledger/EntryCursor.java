package dev.iliamuravev.ledger.ledger;

import dev.iliamuravev.ledger.domain.InvalidRequestException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

record EntryCursor(long beforeEntryId) {

    private static final String PREFIX = "e1:";

    EntryCursor {
        if (beforeEntryId <= 0) {
            throw new InvalidRequestException("Cursor is invalid");
        }
    }

    static EntryCursor decode(String token) {
        try {
            var decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            if (!decoded.startsWith(PREFIX)) {
                throw new InvalidRequestException("Cursor is invalid");
            }
            return new EntryCursor(Long.parseLong(decoded.substring(PREFIX.length())));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Cursor is invalid");
        }
    }

    String encode() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((PREFIX + beforeEntryId).getBytes(StandardCharsets.UTF_8));
    }
}
