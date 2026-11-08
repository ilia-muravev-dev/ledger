package dev.iliamuravev.ledger.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.iliamuravev.ledger.support.IntegrationTest;
import dev.iliamuravev.ledger.support.LedgerApi;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

class LiveUpdatesIT extends IntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Autowired
    ObjectMapper json;

    @LocalServerPort
    int port;

    LedgerApi api;

    @BeforeEach
    void setUp() {
        api = new LedgerApi(http);
    }

    @Test
    void pushesEveryPostedEntryWithTheResultingBalance() throws Exception {
        var received = new LinkedBlockingQueue<JsonNode>();
        var session = connect(received, null);
        try {
            var alice = api.openAccountId("EUR");
            var bob = api.openAccountId("EUR");
            await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1)).until(() -> {
                api.fund(alice, 1_000, "EUR");
                return drainFor(received, alice) != null;
            });
            var transferId = api.transfer(UUID.randomUUID().toString(), alice, bob, 300, "EUR")
                    .getBody().get("id").asText();

            var update = await().atMost(Duration.ofSeconds(20)).until(() -> drainFor(received, bob), u -> u != null);

            assertThat(update.get("type").asText()).isEqualTo("entry-posted");
            assertThat(update.get("transferId").asText()).isEqualTo(transferId);
            assertThat(update.get("counterpartyAccountId").asText()).isEqualTo(alice.toString());
            assertThat(update.get("amount").asLong()).isEqualTo(300);
            assertThat(update.get("balanceAfter").asLong()).isEqualTo(300);
        } finally {
            session.close();
        }
    }

    @Test
    void refusesForeignOrigins() {
        var headers = new WebSocketHttpHeaders();
        headers.setOrigin("https://evil.example");

        assertThatThrownBy(() -> connect(new LinkedBlockingQueue<>(), headers)).isInstanceOf(Exception.class);
    }

    private WebSocketSession connect(LinkedBlockingQueue<JsonNode> received, WebSocketHttpHeaders headers)
            throws Exception {
        var handler = new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                json.readTree(message.getPayload()).forEach(received::add);
            }
        };
        return new StandardWebSocketClient()
                .execute(handler, headers == null ? new WebSocketHttpHeaders() : headers,
                        URI.create("ws://localhost:" + port + "/ws/updates"))
                .get(10, TimeUnit.SECONDS);
    }

    private static JsonNode drainFor(LinkedBlockingQueue<JsonNode> received, UUID accountId) {
        JsonNode match = null;
        JsonNode next;
        while ((next = received.poll()) != null) {
            if (next.get("accountId").asText().equals(accountId.toString())) {
                match = next;
            }
        }
        return match;
    }
}
