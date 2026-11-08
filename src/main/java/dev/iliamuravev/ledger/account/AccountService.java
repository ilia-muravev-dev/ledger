package dev.iliamuravev.ledger.account;

import dev.iliamuravev.ledger.config.LedgerClock;
import dev.iliamuravev.ledger.domain.Account;
import dev.iliamuravev.ledger.domain.AccountNotFoundException;
import dev.iliamuravev.ledger.domain.AccountStatus;
import dev.iliamuravev.ledger.domain.AccountType;
import dev.iliamuravev.ledger.domain.CurrencyCodes;
import dev.iliamuravev.ledger.domain.InvalidRequestException;
import dev.iliamuravev.ledger.domain.Money;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    static final int MAX_OWNER_LENGTH = 200;

    private final AccountRepository accounts;
    private final LedgerClock clock;

    public AccountService(AccountRepository accounts, LedgerClock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional
    public Account open(String owner, String currencyCode) {
        if (owner == null || owner.isBlank() || owner.length() > MAX_OWNER_LENGTH) {
            throw new InvalidRequestException("Owner must be 1 to " + MAX_OWNER_LENGTH + " characters");
        }
        var currency = CurrencyCodes.parse(currencyCode);
        var account = new Account(UUID.randomUUID(), owner.strip(), currency, AccountType.CUSTOMER,
                AccountStatus.ACTIVE, Money.zero(currency), 0, clock.now());
        accounts.insert(account);
        return account;
    }

    @Transactional(readOnly = true)
    public Account get(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }
}
