package dev.iliamuravev.ledger.account;

import static dev.iliamuravev.ledger.jooq.Tables.ACCOUNT;

import dev.iliamuravev.ledger.domain.Account;
import dev.iliamuravev.ledger.domain.Money;
import dev.iliamuravev.ledger.jooq.tables.records.AccountRecord;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
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
