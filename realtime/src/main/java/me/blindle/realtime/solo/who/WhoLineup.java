package me.blindle.realtime.solo.who;

import java.time.LocalDate;
import java.util.List;

/**
 * Personnages du défi « Who's That? » d'un jour : les mêmes pour tout le monde.
 *
 * @param universe     nom affiché de l'univers du jour (ou « Crossover »)
 * @param color        couleur de l'univers (habillage), ou null
 * @param cards        personnages, dans l'ordre de passage
 * @param jackpotIndex position de la légendaire « jackpot » (floue plus longtemps), ou −1
 * @param roster       noms proposés à l'autocomplétion : tout l'univers, triés (ne trahit pas l'ordre)
 */
public record WhoLineup(LocalDate date, String universe, String color, List<WhoCard> cards, int jackpotIndex,
                        List<String> roster) {

    public WhoLineup {
        cards = List.copyOf(cards);
        roster = List.copyOf(roster);
    }

    /** Un personnage du défi (champs génériques du format de thème). */
    public record WhoCard(String id, String name, String rarity, String image, String universe) {
    }
}
