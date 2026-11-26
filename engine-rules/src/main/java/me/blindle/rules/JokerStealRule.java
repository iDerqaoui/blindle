package me.blindle.rules;

import me.blindle.core.event.GameEvent;
import me.blindle.core.event.GameHook;
import me.blindle.core.model.Card;
import me.blindle.core.model.GameState;
import me.blindle.core.model.PlayerState;
import me.blindle.core.model.SlotDef;
import me.blindle.core.rule.Rule;
import me.blindle.core.rule.RuleParams;
import me.blindle.core.rule.RuleResult;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * {@code joker-steal} : rachète la carte d'un adversaire pour {@code multiplier} × son prix
 * d'achat ; la somme est versée à l'adversaire. Cible : {@code "<joueur>/<slot>"}.
 * La carte va dans le même slot s'il est libre, sinon dans le premier slot libre compatible.
 */
@Component
public class JokerStealRule implements Rule {

    public static final String TYPE = "joker-steal";
    public static final String JOKER = "steal";

    public record Params(double multiplier, int usesPerGame) {
        public Params {
            if (multiplier < 1 || usesPerGame < 1) {
                throw new IllegalArgumentException("joker-steal : multiplier ≥ 1 et usesPerGame ≥ 1");
            }
        }

        public static Params from(RuleParams raw) {
            return new Params(raw.getDouble("multiplier", 1.5), raw.getInt("usesPerGame", 1));
        }

        public int priceFor(int paid) {
            return (int) Math.ceil(paid * multiplier);
        }
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Set<GameHook> hooks() {
        return Set.of(GameHook.ON_GAME_START, GameHook.ON_JOKER_USED);
    }

    @Override
    public int priority() {
        return EFFECT;
    }

    @Override
    public void validateParams(RuleParams params) {
        Params.from(params);
    }

    @Override
    public RuleResult apply(GameEvent event, GameState state, RuleParams params) {
        Params p = Params.from(params);
        if (event instanceof GameEvent.GameStarted) {
            return RuleResult.ok(state.withEachPlayer(pl -> pl.withJokers(JOKER, p.usesPerGame())));
        }
        if (!(event instanceof GameEvent.JokerUsed used) || !JOKER.equals(used.jokerId())) {
            return RuleResult.ok(state);
        }
        String[] target = used.targetId() == null ? new String[0] : used.targetId().split("/", 2);
        if (target.length != 2) {
            return RuleResult.reject("BAD_TARGET", "Choisis une carte adverse à voler");
        }
        PlayerState thief = state.player(used.playerId()).orElseThrow();
        Optional<PlayerState> victimOpt = state.player(target[0]);
        if (victimOpt.isEmpty() || victimOpt.get().id().equals(thief.id())) {
            return RuleResult.reject("BAD_TARGET", "Cible invalide");
        }
        PlayerState victim = victimOpt.get();
        String fromSlot = target[1];
        String cardId = victim.team().get(fromSlot);
        if (cardId == null) {
            return RuleResult.reject("BAD_TARGET", "Ce slot est vide");
        }
        Card card = state.card(cardId).orElseThrow();
        Optional<SlotDef> toSlot = destination(state, thief, fromSlot, card);
        if (toSlot.isEmpty()) {
            return RuleResult.reject("NO_FREE_SLOT", "Aucun slot libre compatible pour cette carte");
        }
        int price = p.priceFor(victim.paid().getOrDefault(cardId, 0));
        if (price > thief.budget()) {
            return RuleResult.reject("OVER_BUDGET", "Il faut " + price + " pièces pour voler " + card.name());
        }
        String slot = toSlot.get().id();
        GameState s = state
                .withPlayer(thief.id(), t -> t.withBudget(t.budget() - price)
                        .withCardInSlot(slot, cardId).withCardPaid(cardId, price))
                .withPlayer(victim.id(), v -> v.withoutSlot(fromSlot).withBudget(v.budget() + price))
                .withLog(thief.name() + " a volé " + card.name() + " à " + victim.name() + " pour " + price + " pièces");
        return RuleResult.ok(s);
    }

    private static Optional<SlotDef> destination(GameState s, PlayerState thief, String preferred, Card card) {
        Optional<SlotDef> same = s.slot(preferred)
                .filter(slot -> !thief.team().containsKey(slot.id()) && fits(slot, card));
        if (same.isPresent()) {
            return same;
        }
        return s.slots().stream().filter(slot -> !thief.team().containsKey(slot.id()) && fits(slot, card)).findFirst();
    }

    private static boolean fits(SlotDef slot, Card card) {
        return card.tags().containsAll(slot.requiredTags());
    }
}
