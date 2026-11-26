package me.blindle.rules;

import me.blindle.core.event.GameEvent;
import me.blindle.core.event.GameHook;
import me.blindle.core.model.GameState;
import me.blindle.core.model.PlayerState;
import me.blindle.core.rule.Rule;
import me.blindle.core.rule.RuleParams;
import me.blindle.core.rule.RuleResult;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * {@code seller-collects} : le vendeur du lot (le piocheur en mode {@code drawer}) encaisse le prix
 * payé par le gagnant, au lieu de la banque. Si le vendeur remporte son propre lot, il paie la
 * banque (rien à encaisser). Inspiré de <i>Modern Art</i> : l'or circule, passer n'est plus neutre
 * et le prix d'ouverture devient un bluff risqué.
 */
@Component
public class SellerCollectsRule implements Rule {

    public static final String TYPE = "seller-collects";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Set<GameHook> hooks() {
        return Set.of(GameHook.ON_LOT_WON);
    }

    @Override
    public int priority() {
        return EFFECT;
    }

    @Override
    public Set<String> requiredBidModes() {
        return Set.of("drawer");
    }

    @Override
    public RuleResult apply(GameEvent event, GameState state, RuleParams params) {
        if (!(event instanceof GameEvent.LotWon won) || won.sellerId() == null
                || won.sellerId().equals(won.playerId()) || won.price() <= 0) {
            return RuleResult.ok(state);
        }
        Optional<PlayerState> seller = state.player(won.sellerId());
        if (seller.isEmpty()) {
            return RuleResult.ok(state);
        }
        return RuleResult.ok(state
                .withPlayer(won.sellerId(), p -> p.withBudget(p.budget() + won.price()))
                .withLog(seller.get().name() + " encaisse " + won.price() + " pièces"));
    }
}
