package me.blindle.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import me.blindle.core.model.Card;
import me.blindle.core.model.Hint;
import me.blindle.core.model.SlotDef;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Thème tel que décrit en JSON (docs/PROJECT.md §4), converti en objets moteur à la demande. */
public record Theme(String id, String name, String version, String description, Rights rights, Art art,
                    List<SlotDefinition> defaultSlots, List<String> attributes, List<CardDefinition> cards) {

    public Theme {
        defaultSlots = defaultSlots == null ? List.of() : List.copyOf(defaultSlots);
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
        cards = cards == null ? List.of() : List.copyOf(cards);
    }

    /** Habillage de l'univers (dos de carte, accueil) : affiche, bannière, couleur dominante. Optionnel. */
    public record Art(String cover, String banner, String color) {
    }

    /** Image d'une carte (URL), ou null : le moteur ne la connaît pas, seules les vues l'utilisent. */
    public String imageOf(String cardId) {
        return cards.stream().filter(c -> c.id().equals(cardId)).findFirst()
                .map(CardDefinition::image).orElse(null);
    }

    /** Droits et canaux de diffusion (§8). */
    public record Rights(String profile, List<String> channels, String visibility, String imagePolicy,
                         String disclaimer, String takedownContact) {
        public Rights {
            channels = channels == null ? List.of() : List.copyOf(channels);
        }
    }

    public record SlotDefinition(String id, String label, Set<String> requiredTags) {
        public SlotDef toSlot() {
            return new SlotDef(id, label, requiredTags);
        }
    }

    public record HintDefinition(int level, String type, String text, String value) {
        public Hint toHint() {
            return new Hint(level, type, text != null ? text : value);
        }
    }

    public record CardDefinition(String id, String name, Set<String> tags, String rarity,
                                 @JsonProperty("isTrap") Boolean isTrap, Map<String, Integer> stats,
                                 String image, List<HintDefinition> hints) {
        public Card toCard() {
            return new Card(id, name, tags, stats, rarity, Boolean.TRUE.equals(isTrap),
                    hints.stream().map(HintDefinition::toHint).toList());
        }
    }

    public List<Card> toCards() {
        return cards.stream().map(CardDefinition::toCard).toList();
    }

    public List<SlotDef> toSlots() {
        return defaultSlots.stream().map(SlotDefinition::toSlot).toList();
    }

    /** Le champ optionnel est-il renseigné sur toutes les cartes ? (ex. {@code stats}, {@code rarity}) */
    public boolean allCardsHave(String field) {
        switch (field) {
            case "stats":
                return cards.stream().allMatch(c -> c.stats() != null && !c.stats().isEmpty());
            case "rarity":
                return cards.stream().allMatch(c -> c.rarity() != null);
            case "isTrap":
                return cards.stream().anyMatch(c -> Boolean.TRUE.equals(c.isTrap()));
            case "tags":
                return cards.stream().allMatch(c -> c.tags() != null && !c.tags().isEmpty());
            default:
                return false;
        }
    }
}
