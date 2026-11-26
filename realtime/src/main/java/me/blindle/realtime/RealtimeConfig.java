package me.blindle.realtime;

import me.blindle.core.GameEngine;
import me.blindle.realtime.channel.ChannelAuthenticator;
import me.blindle.realtime.channel.WebChannelAuthenticator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class RealtimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Canal de chaque connexion : web seulement tant que l'authentification Discord (étape 11) n'existe pas. */
    @Bean
    public ChannelAuthenticator channelAuthenticator() {
        return new WebChannelAuthenticator();
    }

    @Bean
    public RoomSettings roomSettings(@Value("${blindle.room.min-players:3}") int minPlayers,
                                     @Value("${blindle.room.slot-timeout-sec:20}") int slotTimeoutSec,
                                     @Value("${blindle.room.vote-timeout-sec:60}") int voteTimeoutSec) {
        return new RoomSettings(minPlayers, slotTimeoutSec, voteTimeoutSec);
    }

    /** Paramètres des rooms hors preset. */
    public record RoomSettings(int minPlayers, int maxPlayers, int slotTimeoutSec, int voteTimeoutSec) {

        public RoomSettings(int minPlayers, int slotTimeoutSec, int voteTimeoutSec) {
            this(minPlayers, GameEngine.MAX_PLAYERS, slotTimeoutSec, voteTimeoutSec);
        }

        /** Variante solo : un seul joueur, qui lance la partie seul. */
        public RoomSettings solo() {
            return new RoomSettings(1, 1, slotTimeoutSec, voteTimeoutSec);
        }
    }
}
