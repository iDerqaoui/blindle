package me.blindle.realtime;

import me.blindle.core.GameEngine;
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
