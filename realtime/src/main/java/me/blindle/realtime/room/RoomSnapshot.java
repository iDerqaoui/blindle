package me.blindle.realtime.room;

import me.blindle.core.model.GameState;
import me.blindle.modes.Vote;
import me.blindle.realtime.game.GameSetup;

import java.time.Instant;
import java.util.List;

/** Copie cohérente de l'état d'une room, prise sous verrou, pour construire les vues. */
public record RoomSnapshot(String code, GameSetup setup, GameState state, String hostId,
                           Instant phaseDeadline, List<Vote> votes, Instant now) {

    public RoomSnapshot {
        votes = List.copyOf(votes);
    }
}
