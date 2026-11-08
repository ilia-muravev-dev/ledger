package dev.iliamuravev.ledger.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.iliamuravev.ledger.domain.InvalidRequestException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EntryCursorTest {

    @Test
    void roundTrips() {
        var cursor = new EntryCursor(1_234_567L);

        assertThat(cursor.encode()).doesNotContain("1234567");
        assertThat(EntryCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "%%%", "MTIz", "ZTE6YWJj", "ZTE6MA", "ZTE6LTU"})
    void rejectsGarbage(String token) {
        assertThatThrownBy(() -> EntryCursor.decode(token)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsCursorsFromAnotherFormat() {
        var foreign = Base64.getUrlEncoder().encodeToString("x9:42".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> EntryCursor.decode(foreign)).isInstanceOf(InvalidRequestException.class);
    }
}
