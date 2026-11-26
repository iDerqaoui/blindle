package me.blindle.realtime.channel;

import me.blindle.content.Channel;
import org.springframework.http.server.ServerHttpRequest;

/**
 * Déduit le canal d'une requête (poignée de main WebSocket ou appel REST) à partir de
 * l'authentification côté serveur — jamais d'un paramètre, en-tête ou champ choisi par le client.
 */
public interface ChannelAuthenticator {

    Channel channelOf(ServerHttpRequest request);
}
