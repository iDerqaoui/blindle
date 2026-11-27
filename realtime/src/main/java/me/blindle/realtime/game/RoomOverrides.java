package me.blindle.realtime.game;

import me.blindle.content.Preset;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Personnalisation d'une room par son hôte, appliquée par-dessus le preset (la base fixée par
 * l'administrateur) et jamais enregistrée : elle disparaît avec la room.
 *
 * @param excludedCards cartes du thème retirées du paquet de cette room
 * @param rarities      rareté imposée à une carte pour cette room (carte → rareté existante du thème)
 * @param rules         règles actives à la place de celles du preset, ou null pour garder celles du preset
 */
public record RoomOverrides(Set<String> excludedCards, Map<String, String> rarities, List<Preset.RuleRef> rules) {

    public static final RoomOverrides NONE = new RoomOverrides(Set.of(), Map.of(), null);

    public RoomOverrides {
        excludedCards = excludedCards == null ? Set.of() : Set.copyOf(excludedCards);
        rarities = rarities == null ? Map.of() : Map.copyOf(rarities);
        rules = rules == null ? null : List.copyOf(rules);
    }

    public boolean isEmpty() {
        return excludedCards.isEmpty() && rarities.isEmpty() && rules == null;
    }
}
