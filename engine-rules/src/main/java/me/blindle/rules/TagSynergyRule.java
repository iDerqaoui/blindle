package me.blindle.rules;

import me.blindle.core.event.GameEvent;
import me.blindle.core.event.GameHook;
import me.blindle.core.model.GameState;
import me.blindle.core.model.PlayerState;
import me.blindle.core.rule.Rule;
import me.blindle.core.rule.RuleParams;
import me.blindle.core.rule.RuleResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * {@code tag-synergy} : au verdict, chaque tag partagé par au moins {@code minCount} cartes
 * d'une équipe rapporte {@code bonus} points à son propriétaire.
 */
@Component
public class TagSynergyRule implements Rule {

    public static final String TYPE = "tag-synergy";

    public record Params(int minCount, int bonus) {
        public Params {
            if (minCount < 2 || bonus < 0) {
                throw new IllegalArgumentException("tag-synergy : minCount ≥ 2 et bonus ≥ 0");
            }
        }

        public static Params from(RuleParams raw) {
            return new Params(raw.getInt("minCount", 2), raw.getInt("bonus", 1));
        }
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Set<GameHook> hooks() {
        return Set.of(GameHook.ON_VERDICT);
    }

    @Override
    public int priority() {
        return EFFECT;
    }

    @Override
    public Set<String> requiredThemeFields() {
        return Set.of("tags");
    }

    @Override
    public void validateParams(RuleParams params) {
        Params.from(params);
    }

    @Override
    public RuleResult apply(GameEvent event, GameState state, RuleParams params) {
        if (!(event instanceof GameEvent.VerdictRequested)) {
            return RuleResult.ok(state);
        }
        Params p = Params.from(params);
        GameState s = state;
        for (PlayerState player : state.players()) {
            Map<String, Integer> counts = new TreeMap<>();
            for (String cardId : player.team().values()) {
                state.card(cardId).ifPresent(c -> c.tags().forEach(t -> counts.merge(t, 1, Integer::sum)));
            }
            int gained = 0;
            for (var e : counts.entrySet()) {
                if (e.getValue() >= p.minCount()) {
                    gained += p.bonus();
                    s = s.withLog("Synergie « " + e.getKey() + " » pour " + player.name() + " (+" + p.bonus() + ")");
                }
            }
            int total = gained;
            s = s.withPlayer(player.id(), pl -> pl.withBonus(pl.bonus() + total));
        }
        return RuleResult.ok(s);
    }
}
