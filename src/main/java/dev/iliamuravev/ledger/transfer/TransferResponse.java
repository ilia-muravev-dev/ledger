package dev.iliamuravev.ledger.transfer;

import dev.iliamuravev.ledger.domain.Transfer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record TransferResponse(
        UUID id,
        String type,
        UUID fromAccountId,
        UUID toAccountId,
        long amount,
        String currency,
        String status,
        String reason,
        FxResponse fx,
        Instant createdAt) {

    static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.id(),
                transfer.type().name(),
                transfer.fromAccountId(),
                transfer.toAccountId(),
                transfer.amount().amount(),
                transfer.amount().currencyCode(),
                transfer.status().name(),
                transfer.reason() == null ? null : transfer.reason().name(),
                transfer.fx() == null ? null : new FxResponse(
                        transfer.fx().target().amount(),
                        transfer.fx().target().currencyCode(),
                        transfer.fx().rate(),
                        transfer.fx().rateSource(),
                        transfer.fx().quotedAt()),
                transfer.createdAt());
    }

    record FxResponse(long targetAmount, String targetCurrency, BigDecimal rate, String rateSource,
            Instant quotedAt) {
    }
}
