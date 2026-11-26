package me.blindle.rules;

import me.blindle.core.event.GameEvent;
import me.blindle.core.event.GameHook;
import me.blindle.core.model.GameState;
import me.blindle.core.model.PlayerState;
import me.blindle.core.rule.Rule;
import me.blindle.core.rule.RuleParams;
import me.blindle.core.rule.RuleResult;
import org.springframework.stereotype.Component;

import java.util.Set;

/** {@code budget-cap} : rejette une offre supérieure au budget restant du joueur. */
@Component
public class BudgetCapRule implements Rule {

    public static final String TYPE = "budget-cap";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Set<GameHook> hooks() {
        return Set.of(GameHook.ON_BID_PLACED);
    }

    @Override
    public int priority() {
        return VALIDATION;
    }

    @Override
    public RuleResult apply(GameEvent event, GameState state, RuleParams params) {
        if (!(event instanceof GameEvent.BidPlaced placed)) {
            return RuleResult.ok(state);
        }
        int budget = state.player(placed.bid().playerId()).map(PlayerState::budget).orElse(0);
        if (placed.bid().amount() > budget) {
            return RuleResult.reject("OVER_BUDGET",
                    "Offre de " + placed.bid().amount() + " supérieure à ton budget (" + budget + ")");
        }
        return RuleResult.ok(state);
    }
}
