package dev.iliamuravev.ledger.live;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration(proxyBeanMethods = false)
@EnableWebSocket
class LiveUpdatesConfig implements WebSocketConfigurer {

    private final LiveUpdatesHandler handler;
    private final String[] allowedOrigins;

    LiveUpdatesConfig(LiveUpdatesHandler handler,
            @Value("${ledger.live.allowed-origin-patterns:http://localhost:*}") String[] allowedOrigins) {
        this.handler = handler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/updates").setAllowedOriginPatterns(allowedOrigins);
    }
}
