package me.blindle.modes;

import me.blindle.core.model.Card;

import java.util.Collection;
import java.util.Map;

/**
 * Valeur d'une carte en pièces, dérivée uniquement des champs génériques du format de thème
 * ({@code stats} 0–100 et {@code rarity}) : aucun thème n'est connu ici.
 * <p>
 * valeur = 2 × moyenne des stats + bonus de rareté. Stat absente : moyenne neutre de 50 ;
 * rareté absente : bonus neutre de {@link #NEUTRAL_RARITY_BONUS}. Ordre de grandeur : 100 à 350,
 * cohérent avec un budget de 1000 pour 5 slots.
 */
public final class CardValuation {

    static final int NEUTRAL_STAT = 50;
    static final int NEUTRAL_RARITY_BONUS = 50;

    private static final Map<String, Integer> RARITY_BONUS = Map.of(
            "common", 25,
            "rare", 50,
            "epic", 100,
            "legendary", 150);

    private CardValuation() {
    }

    public static int of(Card card) {
        double avg = card.stats().isEmpty() ? NEUTRAL_STAT
                : card.stats().values().stream().mapToInt(Integer::intValue).average().orElse(NEUTRAL_STAT);
        int rarity = card.rarity() == null ? NEUTRAL_RARITY_BONUS
                : RARITY_BONUS.getOrDefault(card.rarity(), NEUTRAL_RARITY_BONUS);
        return (int) Math.round(2 * avg) + rarity;
    }

    public static int sum(Collection<Card> cards) {
        return cards.stream().mapToInt(CardValuation::of).sum();
    }
}
