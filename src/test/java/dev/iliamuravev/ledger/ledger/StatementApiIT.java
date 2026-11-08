package dev.iliamuravev.ledger.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import dev.iliamuravev.ledger.support.IntegrationTest;
import dev.iliamuravev.ledger.support.LedgerApi;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

class StatementApiIT extends IntegrationTest {

    @Autowired
    TestRestTemplate http;

    LedgerApi api;

    @BeforeEach
    void setUp() {
        api = new LedgerApi(http);
    }

    @Test
    void pagesThroughAllEntriesNewestFirstWithoutGapsOrDuplicates() {
        var account = api.openAccountId("EUR");
        for (int i = 1; i <= 7; i++) {
            api.fund(account, i, "EUR");
        }

        var amounts = new ArrayList<Long>();
        var pageSizes = new ArrayList<Integer>();
        String cursor = null;
        do {
            var page = page(account, 3, cursor);
            page.get("items").forEach(item -> amounts.add(item.get("amount").asLong()));
            pageSizes.add(page.get("items").size());
            cursor = page.hasNonNull("nextCursor") ? page.get("nextCursor").asText() : null;
        } while (cursor != null);

        assertThat(pageSizes).containsExactly(3, 3, 1);
        assertThat(amounts).containsExactly(7L, 6L, 5L, 4L, 3L, 2L, 1L);
    }

    @Test
    void entriesWrittenWhilePagingDoNotShiftLaterPages() {
        var account = api.openAccountId("EUR");
        for (int i = 1; i <= 4; i++) {
            api.fund(account, i, "EUR");
        }

        var first = page(account, 2, null);
        api.fund(account, 100, "EUR");
        var second = page(account, 2, first.get("nextCursor").asText());

        assertThat(amounts(first)).containsExactly(4L, 3L);
        assertThat(amounts(second)).containsExactly(2L, 1L);
        assertThat(second.hasNonNull("nextCursor")).isFalse();
    }

    @Test
    void entryCarriesTransferAndSignedAmount() {
        var from = api.fundedAccount("EUR", 1_000);
        var to = api.openAccountId("EUR");
        var transferId = api.transfer(UUID.randomUUID().toString(), from, to, 250, "EUR").getBody().get("id");

        var debit = page(from, 1, null).get("items").get(0);

        assertThat(debit.get("transferId")).isEqualTo(transferId);
        assertThat(debit.get("amount").asLong()).isEqualTo(-250);
        assertThat(debit.get("currency").asText()).isEqualTo("EUR");
    }

    @Test
    void emptyAccountHasNoEntries() {
        var page = page(api.openAccountId("EUR"), 10, null);

        assertThat(page.get("items")).isEmpty();
        assertThat(page.hasNonNull("nextCursor")).isFalse();
    }

    @Test
    void validatesLimitAndCursor() {
        var account = api.openAccountId("EUR");

        assertThat(api.get("/accounts/{id}/entries?limit=0", account).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(api.get("/accounts/{id}/entries?limit=201", account).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(api.get("/accounts/{id}/entries?cursor=garbage", account).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownAccountIs404() {
        assertThat(api.get("/accounts/{id}/entries", UUID.randomUUID()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private JsonNode page(UUID account, int limit, String cursor) {
        var response = cursor == null
                ? api.get("/accounts/{id}/entries?limit={limit}", account, limit)
                : api.get("/accounts/{id}/entries?limit={limit}&cursor={cursor}", account, limit, cursor);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static List<Long> amounts(JsonNode page) {
        var amounts = new ArrayList<Long>();
        page.get("items").forEach(item -> amounts.add(item.get("amount").asLong()));
        return amounts;
    }
}
