package dev.iliamuravev.ledger.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public class LedgerApi {

    private final TestRestTemplate http;

    public LedgerApi(TestRestTemplate http) {
        this.http = http;
    }

    public ResponseEntity<JsonNode> openAccount(String owner, String currency) {
        return post("/accounts", null, Map.of("owner", owner, "currency", currency));
    }

    public UUID openAccountId(String currency) {
        var response = openAccount("test-owner", currency);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(response.getBody().get("id").asText());
    }

    public ResponseEntity<JsonNode> getAccount(Object id) {
        return http.getForEntity("/accounts/{id}", JsonNode.class, id);
    }

    public long balance(UUID accountId) {
        var response = getAccount(accountId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().get("balance").asLong();
    }

    public ResponseEntity<JsonNode> post(String path, String idempotencyKey, Object body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }

    public ResponseEntity<JsonNode> get(String path, Object... uriVariables) {
        return http.getForEntity(path, JsonNode.class, uriVariables);
    }
}
