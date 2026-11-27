package me.blindle.realtime;

import me.blindle.realtime.channel.ChannelAuthenticator;
import me.blindle.realtime.stomp.PlayerHandshakeHandler;
import me.blindle.realtime.stomp.SessionTokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuration STOMP : endpoint {@code /ws}, commandes client sur {@code /app/**},
 * diffusions sur {@code /topic/**}, messages privés sur {@code /user/queue/**}.
 * Protocole détaillé : {@code docs/ARCHITECTURE.md} §3.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final String[] allowedOrigins;
    private final ChannelAuthenticator channels;
    private final SessionTokens tokens;

    public WebSocketConfig(@Value("${blindle.allowed-origins:http://localhost:*}") String[] allowedOrigins,
                           ChannelAuthenticator channels, SessionTokens tokens) {
        this.allowedOrigins = allowedOrigins;
        this.channels = channels;
        this.tokens = tokens;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setHandshakeHandler(new PlayerHandshakeHandler(channels, tokens))
                .setAllowedOriginPatterns(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setUserDestinationPrefix("/user");
    }
}
