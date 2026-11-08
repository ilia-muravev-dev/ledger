package dev.iliamuravev.ledger.account;

import static org.assertj.core.api.Assertions.assertThat;

import dev.iliamuravev.ledger.support.IntegrationTest;
import dev.iliamuravev.ledger.support.LedgerApi;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class AccountsApiIT extends IntegrationTest {

    @Autowired
    TestRestTemplate http;

    LedgerApi api;

    @BeforeEach
    void setUp() {
        api = new LedgerApi(http);
    }

    @Test
    void opensAnAccountWithZeroBalance() {
        var response = api.openAccount("Alice", "EUR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var body = response.getBody();
        assertThat(body.get("owner").asText()).isEqualTo("Alice");
        assertThat(body.get("currency").asText()).isEqualTo("EUR");
        assertThat(body.get("type").asText()).isEqualTo("CUSTOMER");
        assertThat(body.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(body.get("balance").asLong()).isZero();
        assertThat(response.getHeaders().getLocation())
                .hasPath("/accounts/" + body.get("id").asText());
    }

    @Test
    void readsAnAccountBack() {
        var id = api.openAccountId("USD");

        var response = api.getAccount(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("id").asText()).isEqualTo(id.toString());
        assertThat(response.getBody().get("currency").asText()).isEqualTo("USD");
    }

    @Test
    void unknownAccountIsAProblem404() {
        var id = UUID.randomUUID();

        var response = api.getAccount(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody().get("type").asText()).isEqualTo("/problems/account-not-found");
        assertThat(response.getBody().get("accountId").asText()).isEqualTo(id.toString());
    }

    @Test
    void malformedAccountIdIs400() {
        var response = api.getAccount("not-a-uuid");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("type").asText()).isEqualTo("/problems/validation-failed");
    }

    @Test
    void rejectsUnknownCurrency() {
        var response = api.openAccount("Alice", "XYZ");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("type").asText()).isEqualTo("/problems/validation-failed");
        assertThat(response.getBody().get("detail").asText()).contains("XYZ");
    }

    @Test
    void reportsFieldErrors() {
        var response = api.post("/accounts", null, Map.of("owner", " ", "currency", ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("errors").has("owner")).isTrue();
        assertThat(response.getBody().get("errors").has("currency")).isTrue();
    }

    @Test
    void malformedJsonIs400() {
        var response = api.post("/accounts", null, "{not json");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("type").asText()).isEqualTo("/problems/validation-failed");
    }
}
