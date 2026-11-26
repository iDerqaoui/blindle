package me.blindle.core.rule;

import me.blindle.core.event.GameEvent;
import me.blindle.core.event.GameHook;
import me.blindle.core.model.GameState;

import java.util.Set;

/**
 * Une règle : déclencheur (hooks) → condition → effet. Sans état : les paramètres
 * du preset sont passés à chaque appel.
 * <p>
 * Priorité : validation &lt; 100, effets ≥ 100.
 */
public interface Rule {

    int VALIDATION = 10;
    int EFFECT = 100;

    /** Identifiant référencé par les presets, ex. {@code rising-floor}. */
    String type();

    Set<GameHook> hooks();

    int priority();

    RuleResult apply(GameEvent event, GameState state, RuleParams params);
}
