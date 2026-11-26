package me.blindle.modes.bot;

import me.blindle.core.model.Bid;
import me.blindle.core.model.Card;
import me.blindle.core.model.Hint;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Trois cartes de valeurs distinctes ; « rouge » est commun à hi et lo au premier indice.
 * hi = 350 (stat 100, legendary, tag star), lo = 65 (stat 20, common), mid = 150 (stat 50, rare).
 */
final class BotFixtures {

    static final Card HI = card("hi", 100, "legendary", Set.of("star"), "rouge", "grand", "H");
    static final Card LO = card("lo", 20, "common", Set.of(), "rouge", "petit", "L");
    static final Card MID = card("mid", 50, "rare", Set.of(), "bleu", "moyen", "M");
    static final List<Card> ALL = List.of(HI, LO, MID);

    private BotFixtures() {
    }

    static Card card(String id, int stat, String rarity, Set<String> tags, String h1, String h2, String h3) {
        return new Card(id, id, tags, Map.of("power", stat), rarity, false,
                List.of(new Hint(1, "text", h1), new Hint(2, "text", h2), new Hint(3, "initial", h3)));
    }

    /** Hints révélés de {@code card} jusqu'au palier {@code level}. */
    static List<Hint> hints(Card card, int level) {
        return card.hints().stream().filter(h -> h.level() <= level).toList();
    }

    /** Bot en enchère scellée, round 1 sur 10, 5 slots libres, budget 1000, plancher 50. */
    static BotView sealed(Card lot, int level) {
        return view(lot, level, false, List.of(), 1, 5, 1000, 50);
    }

    static BotView view(Card lot, int level, boolean visible, List<Bid> bids, int round, int freeSlots,
                        int budget, int floor) {
        return new BotView("bot-1", 42L, visible ? "open" : "sealed", visible, round, 10, budget, 1000,
                freeSlots, 5, level, 3, hints(lot, level), 1, floor, null, bids, ALL);
    }
}
