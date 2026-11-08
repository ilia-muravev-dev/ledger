package dev.iliamuravev.ledger.account;

import static dev.iliamuravev.ledger.jooq.Tables.ACCOUNT;

import dev.iliamuravev.ledger.domain.Account;
import dev.iliamuravev.ledger.domain.AccountStatus;
import dev.iliamuravev.ledger.domain.AccountType;
import dev.iliamuravev.ledger.domain.Money;
import dev.iliamuravev.ledger.jooq.tables.records.AccountRecord;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {

    private final DSLContext db;

    public AccountRepository(DSLContext db) {
        this.db = db;
    }

    public void insert(Account account) {
        db.insertInto(ACCOUNT)
                .set(ACCOUNT.ID, account.id())
                .set(ACCOUNT.OWNER, account.owner())
                .set(ACCOUNT.CURRENCY, account.currency().getCurrencyCode())
                .set(ACCOUNT.TYPE, account.type())
                .set(ACCOUNT.STATUS, account.status())
                .set(ACCOUNT.BALANCE, account.balance().amount())
                .set(ACCOUNT.VERSION, account.version())
                .set(ACCOUNT.CREATED_AT, account.createdAt().atOffset(ZoneOffset.UTC))
                .execute();
    }

    public Optional<Account> findById(UUID id) {
        return db.selectFrom(ACCOUNT)
                .where(ACCOUNT.ID.eq(id))
                .fetchOptional(AccountRepository::toAccount);
    }

    public Optional<Account> lockById(UUID id) {
        return db.selectFrom(ACCOUNT)
                .where(ACCOUNT.ID.eq(id))
                .forUpdate()
                .fetchOptional(AccountRepository::toAccount);
    }

    public long addToBalance(UUID id, long delta) {
        return db.update(ACCOUNT)
                .set(ACCOUNT.BALANCE, ACCOUNT.BALANCE.plus(delta))
                .set(ACCOUNT.VERSION, ACCOUNT.VERSION.plus(1))
                .where(ACCOUNT.ID.eq(id))
                .returningResult(ACCOUNT.BALANCE)
                .fetchOptional(Record1::value1)
                .orElseThrow(() -> new IllegalStateException(
                        "Account " + id + " disappeared while its balance was being updated"));
    }

    public OptionalLong addToBalanceIfVersion(UUID id, long delta, long expectedVersion) {
        return db.update(ACCOUNT)
                .set(ACCOUNT.BALANCE, ACCOUNT.BALANCE.plus(delta))
                .set(ACCOUNT.VERSION, ACCOUNT.VERSION.plus(1))
                .where(ACCOUNT.ID.eq(id))
                .and(ACCOUNT.VERSION.eq(expectedVersion))
                .returningResult(ACCOUNT.BALANCE)
                .fetchOptional(Record1::value1)
                .map(OptionalLong::of)
                .orElse(OptionalLong.empty());
    }

    public Account systemAccount(AccountType type, Currency currency, Instant now) {
        if (type == AccountType.CUSTOMER) {
            throw new IllegalArgumentException("Customer accounts are opened through the API");
        }
        db.insertInto(ACCOUNT)
                .set(ACCOUNT.ID, UUID.randomUUID())
                .set(ACCOUNT.OWNER, type.name().toLowerCase(Locale.ROOT).replace('_', '-'))
                .set(ACCOUNT.CURRENCY, currency.getCurrencyCode())
                .set(ACCOUNT.TYPE, type)
                .set(ACCOUNT.STATUS, AccountStatus.ACTIVE)
                .set(ACCOUNT.BALANCE, 0L)
                .set(ACCOUNT.VERSION, 0L)
                .set(ACCOUNT.CREATED_AT, now.atOffset(ZoneOffset.UTC))
                .onConflictDoNothing()
                .execute();
        return db.selectFrom(ACCOUNT)
                .where(ACCOUNT.CURRENCY.eq(currency.getCurrencyCode()))
                .and(ACCOUNT.TYPE.eq(type))
                .fetchSingle(AccountRepository::toAccount);
    }

    public CloseResult closeIfEmpty(UUID id) {
        var closed = db.update(ACCOUNT)
                .set(ACCOUNT.STATUS, AccountStatus.CLOSED)
                .set(ACCOUNT.VERSION, ACCOUNT.VERSION.plus(1))
                .where(ACCOUNT.ID.eq(id))
                .and(ACCOUNT.TYPE.eq(AccountType.CUSTOMER))
                .and(ACCOUNT.STATUS.eq(AccountStatus.ACTIVE))
                .and(ACCOUNT.BALANCE.eq(0L))
                .execute();
        if (closed == 1) {
            return CloseResult.CLOSED;
        }
        return findById(id)
                .map(account -> account.isActive() ? CloseResult.NOT_EMPTY : CloseResult.ALREADY_CLOSED)
                .orElse(CloseResult.NOT_FOUND);
    }

    public enum CloseResult {
        CLOSED,
        ALREADY_CLOSED,
        NOT_EMPTY,
        NOT_FOUND
    }

    static Account toAccount(AccountRecord r) {
        var currency = Currency.getInstance(r.getCurrency());
        return new Account(
                r.getId(),
                r.getOwner(),
                currency,
                r.getType(),
                r.getStatus(),
                new Money(r.getBalance(), currency),
                r.getVersion(),
                r.getCreatedAt().toInstant());
    }
}
