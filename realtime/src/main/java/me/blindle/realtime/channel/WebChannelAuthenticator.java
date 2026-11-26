package me.blindle.realtime.channel;

import me.blindle.content.Channel;
import org.springframework.http.server.ServerHttpRequest;

/**
 * Authentification actuelle : aucune session Discord n'existe encore, toute connexion est donc
 * sur le canal web. L'étape 11 (Discord Activity) remplacera ce bean par un authentificateur qui
 * reconnaît un jeton Discord échangé côté serveur.
 */
public class WebChannelAuthenticator implements ChannelAuthenticator {

    @Override
    public Channel channelOf(ServerHttpRequest request) {
        return Channel.WEB;
    }
}
