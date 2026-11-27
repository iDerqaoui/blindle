package me.blindle.realtime.stomp;

import me.blindle.content.Channel;
import me.blindle.core.GameException;
import me.blindle.realtime.channel.ChannelAuthenticator;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.Principal;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Attribue une identité serveur à chaque connexion WebSocket. Le joueur est identifié par cette
 * identité, et son canal déduit de l'authentification, jamais d'un champ envoyé par le client.
 */
public class PlayerHandshakeHandler extends DefaultHandshakeHandler {

    /** Identité d'un joueur connecté et canal par lequel il est authentifié. */
    public record PlayerPrincipal(String getName, Channel channel) implements Principal {
        public PlayerPrincipal {
            Objects.requireNonNull(getName, "name");
            Objects.requireNonNull(channel, "channel");
        }

        /** Canal d'un principal de connexion ; refuse tout principal qui n'a pas été attribué ici. */
        public static Channel channelOf(Principal principal) {
            if (principal instanceof PlayerPrincipal p) {
                return p.channel();
            }
            throw new GameException("NOT_AUTHENTICATED", "Connexion non reconnue");
        }
    }

    private final ChannelAuthenticator channels;
    private final SessionTokens tokens;

    public PlayerHandshakeHandler(ChannelAuthenticator channels, SessionTokens tokens) {
        this.channels = channels;
        this.tokens = tokens;
    }

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        // Reconnexion : un jeton authentique (paramètre « resume ») rend l'identité précédente.
        String resume = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("resume");
        String id = tokens.resolve(resume).orElseGet(() -> "p-" + UUID.randomUUID());
        return new PlayerPrincipal(id, channels.channelOf(request));
    }
}
