package dev.iliamuravev.ledger.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.iliamuravev.ledger.support.IntegrationTest;
import dev.iliamuravev.ledger.support.LedgerApi;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
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

    static final Duration EVENTUALLY = Duration.ofSeconds(30);

    @Autowired
    TestRestTemplate http;

    @Autowired
    ObjectMapper json;

    @LocalServerPort
    int port;

    LedgerApi api;

    final List<LiveClient> clients = new ArrayList<>();

    @BeforeEach
    void setUp() {
        api = new LedgerApi(http);
    }

    @AfterEach
    void closeClients() throws IOException {
        for (var client : clients) {
            client.session.close();
        }
    }

    @Test
    void pushesEntriesOfSubscribedAccountsWithTheResultingBalance() throws Exception {
        var alice = api.openAccountId("EUR");
        var bob = api.openAccountId("EUR");
        var client = connect();
        client.subscribe(alice, bob);
        waitUntilLive(client, alice);

        var transferId = api.transfer(UUID.randomUUID().toString(), alice, bob, 300, "EUR")
                .getBody().get("id").asText();

        var update = await().atMost(EVENTUALLY).until(() -> client.latestFor(bob), u -> u != null);
        assertThat(update.get("type").asText()).isEqualTo("entry-posted");
        assertThat(update.get("transferId").asText()).isEqualTo(transferId);
        assertThat(update.get("counterpartyAccountId").asText()).isEqualTo(alice.toString());
        assertThat(update.get("amount").asLong()).isEqualTo(300);
        assertThat(update.get("balanceAfter").asLong()).isEqualTo(300);
    }

    @Test
    void eachConnectionOnlyReceivesItsOwnAccounts() throws Exception {
        var alice = api.openAccountId("EUR");
        var bob = api.openAccountId("EUR");
        var aliceClient = connect();
        var bobClient = connect();
        var silentClient = connect();
        aliceClient.subscribe(alice);
        bobClient.subscribe(bob);
        waitUntilLive(aliceClient, alice);
        aliceClient.updates.clear();
        silentClient.updates.clear();

        api.transfer(UUID.randomUUID().toString(), alice, bob, 100, "EUR");

        await().atMost(EVENTUALLY).until(() -> bobClient.updates.stream().anyMatch(u -> isFor(u, bob)));
        await().atMost(EVENTUALLY).until(() -> aliceClient.updates.stream().anyMatch(u -> isFor(u, alice)));
        assertThat(aliceClient.updates).allSatisfy(u -> assertThat(isFor(u, alice)).isTrue());
        assertThat(bobClient.updates).allSatisfy(u -> assertThat(isFor(u, bob)).isTrue());
        assertThat(silentClient.updates).isEmpty();
    }

    @Test
    void subscribingAgainReplacesTheSet() throws Exception {
        var alice = api.openAccountId("EUR");
        var bob = api.openAccountId("EUR");
        var client = connect();
        client.subscribe(alice);
        waitUntilLive(client, alice);

        client.subscribe(bob);
        client.updates.clear();
        api.fund(alice, 5, "EUR");
        api.fund(bob, 5, "EUR");

        await().atMost(EVENTUALLY).until(() -> client.latestFor(bob) != null);
        assertThat(client.updates).noneMatch(u -> isFor(u, alice));
    }

    @Test
    void rejectsBadSubscriptionsAndKeepsTheConnection() throws Exception {
        var client = connect();

        client.send("not json");
        assertThat(client.nextControl().get("detail").asText()).contains("not valid JSON");
        client.send("{\"type\":\"listen\",\"accountIds\":[]}");
        assertThat(client.nextControl().get("type").asText()).isEqualTo("error");
        client.send("{\"type\":\"subscribe\",\"accountIds\":[\"nope\"]}");
        assertThat(client.nextControl().get("detail").asText()).contains("Not an account id");
        var tooMany = IntStream.range(0, 1_001).mapToObj(i -> UUID.randomUUID().toString()).toList();
        client.send(json.writeValueAsString(Map.of("type", "subscribe", "accountIds", tooMany)));
        assertThat(client.nextControl().get("detail").asText()).contains("At most 1000");

        var account = api.openAccountId("EUR");
        client.subscribe(account);
        waitUntilLive(client, account);
    }

    @Test
    void refusesForeignOrigins() {
        var headers = new WebSocketHttpHeaders();
        headers.setOrigin("https://evil.example");

        assertThatThrownBy(() -> connect(headers)).isInstanceOf(Exception.class);
    }

    private void waitUntilLive(LiveClient client, UUID account) {
        await().atMost(EVENTUALLY).pollInterval(Duration.ofSeconds(1)).until(() -> {
            api.fund(account, 1_000, "EUR");
            return client.latestFor(account) != null;
        });
    }

    private static boolean isFor(JsonNode update, UUID account) {
        return update.get("accountId").asText().equals(account.toString());
    }

    private LiveClient connect() throws Exception {
        return connect(new WebSocketHttpHeaders());
    }

    private LiveClient connect(WebSocketHttpHeaders headers) throws Exception {
        var updates = new LinkedBlockingQueue<JsonNode>();
        var control = new LinkedBlockingQueue<JsonNode>();
        var handler = new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                var node = json.readTree(message.getPayload());
                if (node.isArray()) {
                    node.forEach(updates::add);
                } else {
                    control.add(node);
                }
            }
        };
        var session = new StandardWebSocketClient()
                .execute(handler, headers, URI.create("ws://localhost:" + port + "/ws/updates"))
                .get(10, TimeUnit.SECONDS);
        var client = new LiveClient(session, updates, control);
        clients.add(client);
        return client;
    }

    private final class LiveClient {

        private final WebSocketSession session;
        private final LinkedBlockingQueue<JsonNode> updates;
        private final LinkedBlockingQueue<JsonNode> control;

        private LiveClient(WebSocketSession session, LinkedBlockingQueue<JsonNode> updates,
                LinkedBlockingQueue<JsonNode> control) {
            this.session = session;
            this.updates = updates;
            this.control = control;
        }

        void subscribe(UUID... accounts) throws Exception {
            var ids = Arrays.stream(accounts).map(UUID::toString).toList();
            send(json.writeValueAsString(Map.of("type", "subscribe", "accountIds", ids)));
            var reply = nextControl();
            assertThat(reply.get("type").asText()).isEqualTo("subscribed");
            assertThat(reply.get("accounts").asInt()).isEqualTo(accounts.length);
        }

        void send(String payload) throws IOException {
            session.sendMessage(new TextMessage(payload));
        }

        JsonNode nextControl() throws InterruptedException {
            var next = control.poll(10, TimeUnit.SECONDS);
            assertThat(next).as("control message").isNotNull();
            return next;
        }

        JsonNode latestFor(UUID account) {
            JsonNode latest = null;
            for (var update : updates) {
                if (isFor(update, account)) {
                    latest = update;
                }
            }
            return latest;
        }
    }
}
