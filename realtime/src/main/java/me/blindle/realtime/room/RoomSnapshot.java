package me.blindle.realtime.room;

import me.blindle.core.model.GameState;
import me.blindle.modes.Vote;
import me.blindle.realtime.game.GameSetup;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Copie cohérente de l'état d'une room, prise sous verrou, pour construire les vues.
 *
 * @param history   issue de chaque round terminé, dans l'ordre
 * @param dailyDate jour du défi pour une room « défi du jour », sinon null
 * @param dailyRank rang obtenu au classement du défi du jour, une fois la partie terminée
 */
public record RoomSnapshot(String code, GameSetup setup, GameState state, String hostId,
                           Instant phaseDeadline, List<Vote> votes, Instant now,
                           List<RoundOutcome> history, LocalDate dailyDate, Integer dailyRank) {

    public RoomSnapshot {
        votes = List.copyOf(votes);
        history = List.copyOf(history);
    }
}
