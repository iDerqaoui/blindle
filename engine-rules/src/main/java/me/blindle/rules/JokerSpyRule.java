package me.blindle.rules;

import me.blindle.core.event.GameEvent;
import me.blindle.core.event.GameHook;
import me.blindle.core.model.GameState;
import me.blindle.core.model.Hint;
import me.blindle.core.model.LotState;
import me.blindle.core.rule.Rule;
import me.blindle.core.rule.RuleParams;
import me.blindle.core.rule.RuleResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * {@code joker-spy} : chaque joueur reçoit {@code usesPerGame} jokers « espion ». Activé pendant
 * les enchères, il révèle en privé l'indice du palier suivant. Les indices privés sont
 * effacés en fin de round.
 */
@Component
public class JokerSpyRule implements Rule {

    public static final String TYPE = "joker-spy";
    public static final String JOKER = "spy";

    public record Params(int usesPerGame) {
        public Params {
            if (usesPerGame < 1) {
                throw new IllegalArgumentException("joker-spy : usesPerGame doit être ≥ 1");
            }
        }

        public static Params from(RuleParams raw) {
            return new Params(raw.getInt("usesPerGame", 1));
        }
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Set<GameHook> hooks() {
        return Set.of(GameHook.ON_GAME_START, GameHook.ON_JOKER_USED, GameHook.ON_ROUND_END);
    }

    @Override
    public int priority() {
        return EFFECT;
    }

    @Override
    public Map<String, Object> defaultParams() {
        return RuleParams.defaults("usesPerGame", 1);
    }

    @Override
    public void validateParams(RuleParams params) {
        Params.from(params);
    }

    @Override
    public RuleResult apply(GameEvent event, GameState state, RuleParams params) {
        if (event instanceof GameEvent.GameStarted) {
            int uses = Params.from(params).usesPerGame();
            return RuleResult.ok(state.withEachPlayer(p -> p.withJokers(JOKER, uses)));
        }
        if (event instanceof GameEvent.RoundEnded) {
            return RuleResult.ok(state.withEachPlayer(p -> p.withoutPrivateHints()));
        }
        if (event instanceof GameEvent.JokerUsed used && JOKER.equals(used.jokerId())) {
            LotState lot = state.lot();
            int next = lot.hintLevel() + 1;
            if (next > Math.min(lot.maxHintLevel(), state.settings().hintLevels())) {
                return RuleResult.reject("NO_MORE_HINTS", "Tous les indices sont déjà révélés");
            }
            GameState s = state;
            for (var card : lot.cards()) {
                Hint hint = card.hint(next).orElse(null);
                if (hint != null) {
                    s = s.withPlayer(used.playerId(), p -> p.withPrivateHint(hint));
                }
            }
            String name = state.player(used.playerId()).map(p -> p.name()).orElse("?");
            return RuleResult.ok(s.withLog(name + " utilise un joker espion"));
        }
        return RuleResult.ok(state);
    }
}
