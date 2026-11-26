package me.blindle.core;

import me.blindle.core.model.Card;
import me.blindle.core.model.GameSettings;
import me.blindle.core.model.GameState;
import me.blindle.core.model.Hint;
import me.blindle.core.model.SlotDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Données de test génériques (aucun thème réel). */
public final class Fixtures {

    private Fixtures() {
    }

    public static Card card(String id) {
        return new Card(id, "Carte " + id, Set.of("tag-" + id), Map.of("power", 50), "common", false,
                List.of(new Hint(1, "text", "vague " + id),
                        new Hint(2, "text", "précis " + id),
                        new Hint(3, "initial", id.substring(0, 1))));
    }

    public static List<Card> deck(int size) {
        List<Card> cards = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            cards.add(card("c" + i));
        }
        return cards;
    }

    public static List<SlotDef> slots() {
        return List.of(new SlotDef("captain", "Capitaine"), new SlotDef("right", "Bras droit"),
                new SlotDef("support", "Soutien"));
    }

    public static GameSettings settings(int rounds) {
        return new GameSettings(1000, rounds, 3, 3, 30);
    }

    /** Partie en LOBBY avec trois joueurs. */
    public static GameState lobby(GameEngine engine, long seed, int rounds) {
        GameState s = engine.create("g1", seed, settings(rounds), slots(), deck(10));
        s = engine.addPlayer(s, "p1", "Alice", false).state();
        s = engine.addPlayer(s, "p2", "Bilal", false).state();
        return engine.addPlayer(s, "p3", "Chloé", false).state();
    }
}
