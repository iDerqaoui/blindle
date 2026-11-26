package me.blindle.core.model;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Carte telle que la voit le moteur. Fournie par le module content, opaque pour le moteur :
 * aucun attribut propre à un thème n'est interprété ici.
 *
 * @param rarity optionnelle (null si absente du thème)
 */
public record Card(String id, String name, Set<String> tags, Map<String, Integer> stats,
                   String rarity, boolean trap, List<Hint> hints) {

    public Card {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        stats = stats == null ? Map.of() : Map.copyOf(stats);
        hints = hints == null ? List.of() : hints.stream()
                .sorted(Comparator.comparingInt(Hint::level))
                .toList();
        if (hints.size() < 2) {
            throw new IllegalArgumentException("Carte " + id + " : au moins 2 indices requis");
        }
    }

    public Optional<Hint> hint(int level) {
        return hints.stream().filter(h -> h.level() == level).findFirst();
    }

    public int maxHintLevel() {
        return hints.get(hints.size() - 1).level();
    }
}
