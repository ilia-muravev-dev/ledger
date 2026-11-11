package dev.iliamuravev.ledger.live;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class LiveUpdatesHandler extends TextWebSocketHandler {

    static final int SEND_TIME_LIMIT_MS = 5_000;
    static final int BUFFER_SIZE_LIMIT = 512 * 1024;
    static final int MAX_MESSAGE_CHARS = 64 * 1024;

    private static final Logger log = LoggerFactory.getLogger(LiveUpdatesHandler.class);

    private final Map<String, LiveSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper json;
    private final int maxAccountsPerSession;

    LiveUpdatesHandler(ObjectMapper json, MeterRegistry meters,
            @Value("${ledger.live.max-accounts-per-session:1000}") int maxAccountsPerSession) {
        this.json = json;
        this.maxAccountsPerSession = maxAccountsPerSession;
        Gauge.builder("ledger.live.sessions", sessions, Map::size)
                .description("Open live-update WebSocket sessions")
                .register(meters);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        var decorated = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT,
                ConcurrentWebSocketSessionDecorator.OverflowStrategy.DROP);
        sessions.put(session.getId(), new LiveSession(decorated));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        var live = sessions.get(session.getId());
        if (live == null) {
            return;
        }
        try {
            var accounts = parseSubscription(message.getPayload());
            live.accounts = Set.copyOf(accounts);
            reply(live, Map.of("type", "subscribed", "accounts", accounts.size()));
        } catch (InvalidSubscriptionException e) {
            reply(live, Map.of("type", "error", "detail", e.getMessage()));
        }
    }

    public int connections() {
        return sessions.size();
    }

    public void broadcast(List<LiveUpdate> updates) {
        if (updates.isEmpty() || sessions.isEmpty()) {
            return;
        }
        for (var live : sessions.values()) {
            var accounts = live.accounts;
            if (accounts.isEmpty()) {
                continue;
            }
            var matching = updates.stream().filter(update -> accounts.contains(update.accountId())).toList();
            if (!matching.isEmpty()) {
                send(live, serialize(matching));
            }
        }
    }

    private Set<UUID> parseSubscription(String payload) {
        if (payload.length() > MAX_MESSAGE_CHARS) {
            throw new InvalidSubscriptionException("Message is larger than " + MAX_MESSAGE_CHARS + " characters");
        }
        JsonNode node;
        try {
            node = json.readTree(payload);
        } catch (JsonProcessingException e) {
            throw new InvalidSubscriptionException("Message is not valid JSON");
        }
        if (node == null || !"subscribe".equals(node.path("type").asText()) || !node.path("accountIds").isArray()) {
            throw new InvalidSubscriptionException("Expected {\"type\":\"subscribe\",\"accountIds\":[...]}");
        }
        var ids = node.get("accountIds");
        if (ids.size() > maxAccountsPerSession) {
            throw new InvalidSubscriptionException(
                    "At most " + maxAccountsPerSession + " accounts per connection, got " + ids.size());
        }
        var accounts = new HashSet<UUID>();
        for (var id : ids) {
            try {
                accounts.add(UUID.fromString(id.asText()));
            } catch (IllegalArgumentException e) {
                throw new InvalidSubscriptionException("Not an account id: " + id.asText());
            }
        }
        return accounts;
    }

    private void reply(LiveSession live, Map<String, Object> body) {
        send(live, serialize(body));
    }

    private TextMessage serialize(Object body) {
        try {
            return new TextMessage(json.writeValueAsString(body));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize a live-update message", e);
        }
    }

    private void send(LiveSession live, TextMessage message) {
        var session = live.session;
        if (!session.isOpen()) {
            sessions.remove(session.getId());
            return;
        }
        try {
            session.sendMessage(message);
        } catch (IOException | IllegalStateException e) {
            log.debug("Dropping live update session {}: {}", session.getId(), e.getMessage());
            sessions.remove(session.getId());
        }
    }

    private static final class LiveSession {

        private final WebSocketSession session;
        private volatile Set<UUID> accounts = Set.of();

        private LiveSession(WebSocketSession session) {
            this.session = session;
        }
    }

    private static final class InvalidSubscriptionException extends RuntimeException {

        private InvalidSubscriptionException(String message) {
            super(message, null, false, false);
        }
    }
}
