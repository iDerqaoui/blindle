package me.blindle.modes.bot;

import me.blindle.core.GameRandom;
import me.blindle.core.model.Card;
import me.blindle.core.model.Hint;
import me.blindle.modes.CardValuation;

import java.util.List;

/**
 * Estimation de la valeur du lot par un bot : il filtre les cartes compatibles avec les indices
 * révélés, puis mélange leur valeur moyenne avec celle de toutes les cartes encore en jeu, avec
 * une confiance qui croît avec les indices (palier / nombre de paliers). Un bruit personnel de
 * ±{@link #NOISE} évite que tous les bots pensent pareil.
 */
public final class BotEstimator {

    static final double NOISE = 0.10;

    private BotEstimator() {
    }

    public static double estimate(BotView v) {
        List<Card> pool = v.unseen();
        if (pool.isEmpty()) {
            return 0;
        }
        double prior = mean(pool);
        List<Card> candidates = v.lotSize() == 1 ? candidates(v) : List.of();
        double informed = candidates.isEmpty() ? prior : mean(candidates);
        double confidence = v.maxHintLevel() <= 0 ? 0 : (double) v.hintLevel() / v.maxHintLevel();
        double perCard = confidence * informed + (1 - confidence) * prior;
        GameRandom random = GameRandom.derive(v.botSeed(), "estimate:" + v.round());
        double noise = 1 - NOISE + 2 * NOISE * random.nextDouble();
        return perCard * v.lotSize() * noise;
    }

    /** Cartes encore en jeu dont les indices aux paliers révélés sont exactement ceux du lot. */
    public static List<Card> candidates(BotView v) {
        return v.unseen().stream().filter(c -> matches(c, v.hints())).toList();
    }

    private static boolean matches(Card card, List<Hint> revealed) {
        for (Hint h : revealed) {
            boolean same = card.hint(h.level())
                    .map(x -> x.type().equals(h.type()) && x.value().equals(h.value()))
                    .orElse(false);
            if (!same) {
                return false;
            }
        }
        return true;
    }

    private static double mean(List<Card> cards) {
        return cards.stream().mapToInt(CardValuation::of).average().orElse(0);
    }
}
