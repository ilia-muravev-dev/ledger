package dev.iliamuravev.ledger.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import dev.iliamuravev.ledger.support.IntegrationTest;
import dev.iliamuravev.ledger.support.LedgerApi;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

class AccountListIT extends IntegrationTest {

    @Autowired
    TestRestTemplate http;

    LedgerApi api;

    @BeforeEach
    void setUp() {
        api = new LedgerApi(http);
    }

    @Test
    void listsCustomerAccountsNewestFirstAcrossPages() {
        var first = api.openAccountId("EUR").toString();
        var second = api.openAccountId("USD").toString();
        var third = api.openAccountId("GBP").toString();
        api.fund(api.openAccountId("CHF"), 1, "CHF");
        var fourth = api.get("/accounts?limit=1").getBody().get("items").get(0).get("id").asText();

        var pageOne = api.get("/accounts?limit=2").getBody();
        var pageTwo = api.get("/accounts?limit=2&cursor={cursor}", pageOne.get("nextCursor").asText()).getBody();

        assertThat(ids(pageOne)).containsExactly(fourth, third);
        assertThat(ids(pageTwo)).containsExactly(second, first);
        assertThat(pageOne.get("items")).allSatisfy(item -> assertThat(item.get("type").asText()).isEqualTo("CUSTOMER"));
    }

    @Test
    void walksTheWholeListWithoutDuplicates() {
        api.openAccountId("EUR");
        var seen = new ArrayList<String>();
        String cursor = null;
        do {
            var page = cursor == null ? api.get("/accounts?limit=7").getBody()
                    : api.get("/accounts?limit=7&cursor={c}", cursor).getBody();
            seen.addAll(ids(page));
            cursor = page.hasNonNull("nextCursor") ? page.get("nextCursor").asText() : null;
        } while (cursor != null);

        assertThat(seen).isNotEmpty().doesNotHaveDuplicates();
    }

    @Test
    void validatesLimitAndCursor() {
        assertThat(api.get("/accounts?limit=0").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(api.get("/accounts?limit=201").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(api.get("/accounts?cursor=nope").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static List<String> ids(JsonNode page) {
        var ids = new ArrayList<String>();
        page.get("items").forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }
}
