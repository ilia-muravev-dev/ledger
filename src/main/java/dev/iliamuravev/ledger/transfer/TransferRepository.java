package dev.iliamuravev.ledger.transfer;

import static dev.iliamuravev.ledger.jooq.Tables.TRANSFER;

import dev.iliamuravev.ledger.domain.FxConversion;
import dev.iliamuravev.ledger.domain.Money;
import dev.iliamuravev.ledger.domain.Transfer;
import dev.iliamuravev.ledger.jooq.tables.records.TransferRecord;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class TransferRepository {

    private final DSLContext db;

    public TransferRepository(DSLContext db) {
        this.db = db;
    }

    public void insert(Transfer transfer) {
        db.insertInto(TRANSFER)
                .set(TRANSFER.ID, transfer.id())
                .set(TRANSFER.IDEMPOTENCY_KEY, transfer.idempotencyKey())
                .set(TRANSFER.TYPE, transfer.type())
                .set(TRANSFER.FROM_ACCOUNT_ID, transfer.fromAccountId())
                .set(TRANSFER.TO_ACCOUNT_ID, transfer.toAccountId())
                .set(TRANSFER.AMOUNT, transfer.amount().amount())
                .set(TRANSFER.CURRENCY, transfer.amount().currencyCode())
                .set(TRANSFER.STATUS, transfer.status())
                .set(TRANSFER.REASON, transfer.reason())
                .set(TRANSFER.TARGET_AMOUNT, transfer.fx() == null ? null : transfer.fx().target().amount())
                .set(TRANSFER.TARGET_CURRENCY, transfer.fx() == null ? null : transfer.fx().target().currencyCode())
                .set(TRANSFER.FX_RATE, transfer.fx() == null ? null : transfer.fx().rate())
                .set(TRANSFER.FX_RATE_SOURCE, transfer.fx() == null ? null : transfer.fx().rateSource())
                .set(TRANSFER.FX_QUOTED_AT, transfer.fx() == null ? null
                        : transfer.fx().quotedAt().atOffset(ZoneOffset.UTC))
                .set(TRANSFER.CREATED_AT, transfer.createdAt().atOffset(ZoneOffset.UTC))
                .execute();
    }

    public Optional<Transfer> findById(UUID id) {
        return db.selectFrom(TRANSFER)
                .where(TRANSFER.ID.eq(id))
                .fetchOptional(TransferRepository::toTransfer);
    }

    private static Transfer toTransfer(TransferRecord r) {
        return new Transfer(
                r.getId(),
                r.getIdempotencyKey(),
                r.getType(),
                r.getFromAccountId(),
                r.getToAccountId(),
                new Money(r.getAmount(), Currency.getInstance(r.getCurrency())),
                r.getStatus(),
                r.getReason(),
                r.getTargetCurrency() == null ? null : new FxConversion(
                        new Money(r.getTargetAmount(), Currency.getInstance(r.getTargetCurrency())),
                        r.getFxRate(),
                        r.getFxRateSource(),
                        r.getFxQuotedAt().toInstant()),
                r.getCreatedAt().toInstant());
    }
}
