package me.blindle.rules;

import me.blindle.core.GameRandom;
import me.blindle.core.event.GameEvent;
import me.blindle.core.event.GameHook;
import me.blindle.core.model.Card;
import me.blindle.core.model.GameState;
import me.blindle.core.rule.Rule;
import me.blindle.core.rule.RuleParams;
import me.blindle.core.rule.RuleResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code trap-lots} : au démarrage, place des cartes pièges ({@code isTrap}, aux indices
 * flatteurs) parmi les lots joués, chaque round ayant une probabilité {@code frequency}
 * d'en être un. À la révélation, le gagnant d'un piège perd {@code penalty} pièces.
 */
@Component
public class TrapLotsRule implements Rule {

    public static final String TYPE = "trap-lots";

    public record Params(double frequency, int penalty) {
        public Params {
            if (frequency < 0 || frequency > 1 || penalty < 0) {
                throw new IllegalArgumentException("trap-lots : 0 ≤ frequency ≤ 1 et penalty ≥ 0");
            }
        }

        public static Params from(RuleParams raw) {
            return new Params(raw.getDouble("frequency", 0.2), raw.getInt("penalty", 50));
        }
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Set<GameHook> hooks() {
        return Set.of(GameHook.ON_GAME_START, GameHook.ON_CARD_REVEALED);
    }

    @Override
    public int priority() {
        return EFFECT;
    }

    @Override
    public Set<String> requiredThemeFields() {
        return Set.of("isTrap");
    }

    @Override
    public Map<String, Object> defaultParams() {
        return RuleParams.defaults("frequency", 0.2, "penalty", 50);
    }

    @Override
    public void validateParams(RuleParams params) {
        Params.from(params);
    }

    @Override
    public RuleResult apply(GameEvent event, GameState state, RuleParams params) {
        Params p = Params.from(params);
        if (event instanceof GameEvent.GameStarted) {
            return RuleResult.ok(state.withDeck(placeTraps(state, p.frequency())));
        }
        if (event instanceof GameEvent.CardRevealed revealed
                && state.lot().cards().stream().anyMatch(Card::trap) && revealed.winnerId() != null) {
            var winner = state.player(revealed.winnerId()).orElseThrow();
            int loss = Math.min(p.penalty(), winner.budget());
            return RuleResult.ok(state
                    .withPlayer(winner.id(), w -> w.withBudget(w.budget() - loss))
                    .withLog("Piège ! " + winner.name() + " perd " + loss + " pièces"));
        }
        return RuleResult.ok(state);
    }

    /** Pour chaque round joué, tire (par la seed) s'il s'agit d'un piège. */
    private static List<Card> placeTraps(GameState s, double frequency) {
        List<Card> traps = new ArrayList<>(s.deck().stream().filter(Card::trap).toList());
        List<Card> normal = new ArrayList<>(s.deck().stream().filter(c -> !c.trap()).toList());
        GameRandom random = GameRandom.derive(s.seed(), "traps");
        List<Card> deck = new ArrayList<>();
        for (int round = 0; round < s.settings().rounds() && !(traps.isEmpty() && normal.isEmpty()); round++) {
            // Tirage fait à chaque round, même sans piège restant, pour garder la séquence stable.
            boolean roll = random.nextDouble() < frequency;
            boolean trap = !traps.isEmpty() && (normal.isEmpty() || roll);
            deck.add(trap ? traps.remove(0) : normal.remove(0));
        }
        deck.addAll(normal);
        deck.addAll(traps);
        return deck;
    }
}
