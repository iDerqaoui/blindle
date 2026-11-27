package me.blindle.core.rule;

import me.blindle.core.event.GameEvent;
import me.blindle.core.event.GameHook;
import me.blindle.core.model.GameState;

import java.util.Map;
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

    /**
     * Champs de thème requis : {@code stats}, {@code rarity}, {@code tags}, {@code isTrap}
     * (au moins une carte piège) ou {@code slotTags} (au moins un slot avec tags requis).
     */
    default Set<String> requiredThemeFields() {
        return Set.of();
    }

    /** Modes d'enchère compatibles (vide = tous). */
    default Set<String> requiredBidModes() {
        return Set.of();
    }

    /** Règles incompatibles avec celle-ci. */
    default Set<String> conflictsWith() {
        return Set.of();
    }

    /**
     * Paramètres par défaut, dans l'ordre d'affichage : ce qu'un hôte voit et peut régler en activant
     * la règle pour sa room. Vide si la règle n'a pas de paramètre.
     */
    default Map<String, Object> defaultParams() {
        return Map.of();
    }

    /** Vérifie les paramètres au chargement du preset ; lève IllegalArgumentException s'ils sont invalides. */
    default void validateParams(RuleParams params) {
    }
}
