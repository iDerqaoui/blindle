package me.blindle.core;

import me.blindle.core.event.GameEvent;
import me.blindle.core.model.GameState;

import java.util.List;

/** Résultat d'une action : nouvel état et événements émis, dans l'ordre. */
public record Step(GameState state, List<GameEvent> events) {

    public Step {
        events = List.copyOf(events);
    }
}
