package dev.iliamuravev.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import dev.iliamuravev.ledger.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

class LedgerApplicationIT extends IntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Test
    void healthIsUp() {
        var response = http.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
