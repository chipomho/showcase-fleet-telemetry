package au.chipomho.showcase.fleet.live;

import au.chipomho.showcase.fleet.FleetProperties;
import au.chipomho.showcase.fleet.telemetry.TruckStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import tools.jackson.databind.json.JsonMapper;

/**
 * Opens {@code /v1/live} to the allowed origins only; a browser on any other origin is refused
 * during the handshake.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
class LiveFeedConfiguration implements WebSocketConfigurer {

    private final LiveFeed liveFeed;
    private final FleetProperties properties;

    LiveFeedConfiguration(LiveFeed liveFeed, FleetProperties properties) {
        this.liveFeed = liveFeed;
        this.properties = properties;
    }

    @Bean
    static LiveFeed liveFeed(TruckStore trucks, JsonMapper json) {
        return new LiveFeed(trucks::all, json);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(liveFeed, "/v1/live").setAllowedOrigins(properties.allowedOrigins().toArray(String[]::new));
    }
}
