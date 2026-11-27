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
import java.util.Map;
import java.util.Set;

/**
 * {@code discard-fee} : quand un lot part à la défausse faute d'acheteur, son vendeur (le piocheur)
 * paie {@code amount} pièces (150 par défaut) à la banque, dans la limite de son or. Refuser une carte
 * reste possible, mais coûte cher : on ne peut plus vider le paquet pour attendre les meilleures.
 */
@Component
public class DiscardFeeRule implements Rule {

    public static final String TYPE = "discard-fee";
    /** Laisser filer une carte doit coûter cher : 15 % d'un budget de 1000. */
    public static final int DEFAULT_AMOUNT = 150;

    public record Params(int amount) {
        public Params {
            if (amount < 0 || amount > 1000) {
                throw new IllegalArgumentException("discard-fee : amount entre 0 et 1000");
            }
        }

        public static Params from(RuleParams raw) {
            return new Params(raw.getInt("amount", DEFAULT_AMOUNT));
        }
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Set<GameHook> hooks() {
        return Set.of(GameHook.ON_LOT_DISCARDED);
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
    public Map<String, Object> defaultParams() {
        return RuleParams.defaults("amount", DEFAULT_AMOUNT);
    }

    @Override
    public void validateParams(RuleParams params) {
        Params.from(params);
    }

    @Override
    public RuleResult apply(GameEvent event, GameState state, RuleParams params) {
        if (!(event instanceof GameEvent.LotDiscarded discarded) || discarded.sellerId() == null) {
            return RuleResult.ok(state);
        }
        Optional<PlayerState> seller = state.player(discarded.sellerId());
        if (seller.isEmpty()) {
            return RuleResult.ok(state);
        }
        int fee = Math.min(Params.from(params).amount(), seller.get().budget());
        if (fee == 0) {
            return RuleResult.ok(state);
        }
        return RuleResult.ok(state
                .withPlayer(discarded.sellerId(), p -> p.withBudget(p.budget() - fee))
                .withLog(seller.get().name() + " paie " + fee + " pièces pour défausser"));
    }
}
