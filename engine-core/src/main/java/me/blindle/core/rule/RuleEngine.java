package me.blindle.core.rule;

import me.blindle.core.HookDispatcher;
import me.blindle.core.event.GameEvent;
import me.blindle.core.event.GameHook;
import me.blindle.core.model.GameState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Dispatch des événements vers les règles actives d'une partie : filtrage par hook,
 * tri par priorité croissante (ordre de déclaration à priorité égale), arrêt au premier rejet.
 */
public final class RuleEngine implements HookDispatcher {

    /** Règle activée par un preset, avec ses paramètres. */
    public record ActiveRule(Rule rule, RuleParams params) {
        public ActiveRule {
            Objects.requireNonNull(rule, "rule");
            params = params == null ? RuleParams.EMPTY : params;
        }
    }

    private final Map<GameHook, List<ActiveRule>> byHook = new EnumMap<>(GameHook.class);

    public RuleEngine(List<ActiveRule> rules) {
        for (GameHook hook : GameHook.values()) {
            List<ActiveRule> matching = new ArrayList<>();
            for (ActiveRule r : rules) {
                if (r.rule().hooks().contains(hook)) {
                    matching.add(r);
                }
            }
            // Tri stable : l'ordre de déclaration départage les priorités égales.
            matching.sort(Comparator.comparingInt(r -> r.rule().priority()));
            byHook.put(hook, List.copyOf(matching));
        }
    }

    public List<ActiveRule> rulesFor(GameHook hook) {
        return byHook.get(hook);
    }

    @Override
    public GameState dispatch(GameEvent event, GameState state) {
        GameState current = state;
        for (ActiveRule active : byHook.get(event.hook())) {
            RuleResult result = active.rule().apply(event, current, active.params());
            if (result instanceof RuleResult.Reject reject) {
                throw new RuleRejectedException(active.rule().type(), reject.code(), reject.message());
            }
            current = ((RuleResult.Continue) result).state();
        }
        return current;
    }
}
