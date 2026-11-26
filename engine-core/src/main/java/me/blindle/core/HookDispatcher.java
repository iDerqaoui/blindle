package me.blindle.core;

import me.blindle.core.event.GameEvent;
import me.blindle.core.model.GameState;

/**
 * Point de branchement des règles : reçoit chaque événement et renvoie l'état modifié.
 * Peut lever une {@link GameException} pour rejeter l'action en cours ; l'état précédent
 * reste alors inchangé (immutabilité).
 */
@FunctionalInterface
public interface HookDispatcher {

    HookDispatcher NONE = (event, state) -> state;

    GameState dispatch(GameEvent event, GameState state);
}
