package dev.iliamuravev.ledger.account;

import dev.iliamuravev.ledger.domain.Account;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/accounts")
class AccountController {

    private final AccountService accounts;

    AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping
    ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request) {
        var account = accounts.open(request.owner(), request.currency());
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(account.id())
                .toUri();
        return ResponseEntity.created(location).body(AccountResponse.from(account));
    }

    @GetMapping("/{id}")
    AccountResponse get(@PathVariable UUID id) {
        return AccountResponse.from(accounts.get(id));
    }

    record OpenAccountRequest(
            @NotBlank @Size(max = AccountService.MAX_OWNER_LENGTH) String owner,
            @NotBlank String currency) {
    }

    record AccountResponse(
            UUID id,
            String owner,
            String currency,
            String type,
            String status,
            long balance,
            Instant createdAt) {

        static AccountResponse from(Account account) {
            return new AccountResponse(
                    account.id(),
                    account.owner(),
                    account.currency().getCurrencyCode(),
                    account.type().name(),
                    account.status().name(),
                    account.balance().amount(),
                    account.createdAt());
        }
    }
}
