package me.blindle.rules;

import me.blindle.core.event.GameEvent;
import me.blindle.core.event.GameHook;
import me.blindle.core.model.GameState;
import me.blindle.core.rule.Rule;
import me.blindle.core.rule.RuleParams;
import me.blindle.core.rule.RuleResult;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * {@code rising-floor} : le prix plancher vaut {@code base + stepPerHint × (palier − 1)}.
 * Le moteur refuse ensuite toute offre inférieure au plancher.
 */
@Component
public class RisingFloorRule implements Rule {

    public static final String TYPE = "rising-floor";

    /** Paramètres typés. */
    public record Params(int base, int stepPerHint) {

        public Params {
            if (base < 0 || stepPerHint < 0) {
                throw new IllegalArgumentException("rising-floor : base et stepPerHint doivent être ≥ 0");
            }
        }

        public static Params from(RuleParams raw) {
            return new Params(raw.getInt("base", 50), raw.getInt("stepPerHint", 50));
        }

        public int floorAt(int hintLevel) {
            return base + stepPerHint * Math.max(0, hintLevel - 1);
        }
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Set<GameHook> hooks() {
        return Set.of(GameHook.ON_HINT_REVEAL);
    }

    @Override
    public int priority() {
        return EFFECT;
    }

    @Override
    public RuleResult apply(GameEvent event, GameState state, RuleParams params) {
        if (!(event instanceof GameEvent.HintRevealed revealed) || state.lot() == null) {
            return RuleResult.ok(state);
        }
        int floor = Params.from(params).floorAt(revealed.level());
        return RuleResult.ok(state.withLot(state.lot().withFloorPrice(floor)));
    }
}
