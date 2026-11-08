package dev.iliamuravev.ledger.live;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(LiveUpdatesHandler.class);

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper json;

    LiveUpdatesHandler(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS,
                BUFFER_SIZE_LIMIT, ConcurrentWebSocketSessionDecorator.OverflowStrategy.DROP));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    public int connections() {
        return sessions.size();
    }

    public void broadcast(List<LiveUpdate> updates) {
        if (updates.isEmpty() || sessions.isEmpty()) {
            return;
        }
        TextMessage message;
        try {
            message = new TextMessage(json.writeValueAsString(updates));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize live updates", e);
        }
        sessions.values().forEach(session -> send(session, message));
    }

    private void send(WebSocketSession session, TextMessage message) {
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
}
