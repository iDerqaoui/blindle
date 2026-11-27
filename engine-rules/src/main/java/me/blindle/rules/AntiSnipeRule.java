package me.blindle.rules;

import me.blindle.core.event.GameEvent;
import me.blindle.core.event.GameHook;
import me.blindle.core.model.GameState;
import me.blindle.core.model.LotState;
import me.blindle.core.rule.Rule;
import me.blindle.core.rule.RuleParams;
import me.blindle.core.rule.RuleResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * {@code anti-snipe} : une offre reçue à moins de {@code extendSeconds} de la fin repousse
 * l'heure de fin à « réception + extendSeconds ». Réservé aux modes à offres visibles ({@code open}, {@code drawer}).
 */
@Component
public class AntiSnipeRule implements Rule {

    public static final String TYPE = "anti-snipe";

    public record Params(int extendSeconds) {
        public Params {
            if (extendSeconds < 1 || extendSeconds > 60) {
                throw new IllegalArgumentException("anti-snipe : extendSeconds entre 1 et 60");
            }
        }

        public static Params from(RuleParams raw) {
            return new Params(raw.getInt("extendSeconds", 5));
        }
    }

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
        return EFFECT;
    }

    @Override
    public Set<String> requiredBidModes() {
        return Set.of("open", "drawer");
    }

    @Override
    public Map<String, Object> defaultParams() {
        return RuleParams.defaults("extendSeconds", 5);
    }

    @Override
    public void validateParams(RuleParams params) {
        Params.from(params);
    }

    @Override
    public RuleResult apply(GameEvent event, GameState state, RuleParams params) {
        if (!(event instanceof GameEvent.BidPlaced placed) || placed.at() == null) {
            return RuleResult.ok(state);
        }
        LotState lot = state.lot();
        if (lot.deadline() == null) {
            return RuleResult.ok(state);
        }
        Instant extended = placed.at().plusSeconds(Params.from(params).extendSeconds());
        if (extended.isAfter(lot.deadline())) {
            return RuleResult.ok(state.withLot(lot.withDeadline(extended)));
        }
        return RuleResult.ok(state);
    }
}
