package me.blindle.realtime.stomp;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

/**
 * Attribue une identité serveur à chaque connexion WebSocket. Le joueur est identifié
 * par cette identité, jamais par un champ envoyé par le client.
 */
public class PlayerHandshakeHandler extends DefaultHandshakeHandler {

    /** Identité d'un joueur connecté. */
    public record PlayerPrincipal(String getName) implements Principal {
    }

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        return new PlayerPrincipal("p-" + UUID.randomUUID());
    }
}
