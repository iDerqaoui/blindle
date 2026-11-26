package me.blindle.modes;

import me.blindle.core.model.Card;

import java.util.Collection;
import java.util.Map;

/**
 * Points d'une carte selon sa rareté (champ générique du format de thème) : aucun thème n'est connu ici.
 * <p>
 * Barème 10 / 20 / 35 / 60. Une légendaire vaut six communes mais moins que deux épiques, si bien
 * qu'une équipe régulière (5 épiques = 175) bat une équipe « 1 légendaire + 4 communes » (100) :
 * dépenser tout son budget sur une seule star ne suffit pas. Rareté absente ou inconnue : 0.
 */
public final class RarityPoints {

    public static final Map<String, Integer> POINTS = Map.of(
            "common", 10,
            "rare", 20,
            "epic", 35,
            "legendary", 60);

    private RarityPoints() {
    }

    public static int of(Card card) {
        return card.rarity() == null ? 0 : POINTS.getOrDefault(card.rarity(), 0);
    }

    public static int sum(Collection<Card> cards) {
        return cards.stream().mapToInt(RarityPoints::of).sum();
    }
}
