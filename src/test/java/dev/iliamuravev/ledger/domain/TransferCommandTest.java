package dev.iliamuravev.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferCommandTest {

    private final UUID from = UUID.randomUUID();
    private final UUID to = UUID.randomUUID();

    @Test
    void acceptsAValidCommand() {
        var command = new TransferCommand("key-1", TransferType.TRANSFER, from, to, Money.of(100, "EUR"));

        assertThat(command.amount()).isEqualTo(Money.of(100, "EUR"));
    }

    @Test
    void rejectsNonPositiveAmounts() {
        assertThatThrownBy(() -> new TransferCommand("k", TransferType.TRANSFER, from, to, Money.of(0, "EUR")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("greater than zero");
        assertThatThrownBy(() -> new TransferCommand("k", TransferType.TRANSFER, from, to, Money.of(-1, "EUR")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsTransferToTheSameAccount() {
        assertThatThrownBy(() -> new TransferCommand("k", TransferType.TRANSFER, from, from, Money.of(1, "EUR")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("same account");
    }

    @Test
    void rejectsBlankOrOversizedIdempotencyKeys() {
        assertThatThrownBy(() -> new TransferCommand(" ", TransferType.TRANSFER, from, to, Money.of(1, "EUR")))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> new TransferCommand("x".repeat(256), TransferType.TRANSFER, from, to,
                Money.of(1, "EUR")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void fingerprintDependsOnPayloadButNotOnIdempotencyKey() {
        var original = new TransferCommand("a", TransferType.TRANSFER, from, to, Money.of(100, "EUR"));
        var sameBodyOtherKey = new TransferCommand("b", TransferType.TRANSFER, from, to, Money.of(100, "EUR"));
        var otherAmount = new TransferCommand("a", TransferType.TRANSFER, from, to, Money.of(101, "EUR"));
        var otherCurrency = new TransferCommand("a", TransferType.TRANSFER, from, to, Money.of(100, "USD"));
        var otherType = new TransferCommand("a", TransferType.DEPOSIT, from, to, Money.of(100, "EUR"));
        var swapped = new TransferCommand("a", TransferType.TRANSFER, to, from, Money.of(100, "EUR"));

        assertThat(original.fingerprint())
                .hasSize(64)
                .isEqualTo(sameBodyOtherKey.fingerprint())
                .isNotEqualTo(otherAmount.fingerprint())
                .isNotEqualTo(otherCurrency.fingerprint())
                .isNotEqualTo(otherType.fingerprint())
                .isNotEqualTo(swapped.fingerprint());
    }
}
