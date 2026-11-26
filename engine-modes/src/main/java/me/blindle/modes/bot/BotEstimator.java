package me.blindle.modes.bot;

import me.blindle.core.GameRandom;
import me.blindle.core.model.Card;
import me.blindle.core.model.Hint;
import me.blindle.modes.CardValuation;
import me.blindle.modes.PointsVerdictMode;
import me.blindle.modes.RarityPoints;

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
        if (v.drawnCard() != null) {
            GameRandom random = GameRandom.derive(v.botSeed(), "estimate:" + v.round());
            return value(v.drawnCard(), v) * (1 - NOISE + 2 * NOISE * random.nextDouble());
        }
        List<Card> pool = v.unseen();
        if (pool.isEmpty()) {
            return 0;
        }
        double prior = mean(pool, v);
        List<Card> candidates = v.lotSize() == 1 ? candidates(v) : List.of();
        double informed = candidates.isEmpty() ? prior : mean(candidates, v);
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

    /**
     * Valeur d'une carte en pièces pour le bot. Verdict {@code points} : {@link #GOLD_PER_RARITY_POINT}
     * pièces par point de rareté (commune 100 … légendaire 600, à l'échelle d'un budget de 1000 pour
     * 5 slots) ; sinon {@link CardValuation}.
     */
    static double value(Card card, BotView v) {
        return PointsVerdictMode.ID.equals(v.verdictMode())
                ? RarityPoints.of(card) * GOLD_PER_RARITY_POINT
                : CardValuation.of(card);
    }

    static final int GOLD_PER_RARITY_POINT = 10;

    private static double mean(List<Card> cards, BotView v) {
        return cards.stream().mapToDouble(c -> value(c, v)).average().orElse(0);
    }
}
