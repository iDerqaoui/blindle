package me.blindle.rules;

import me.blindle.core.Fixtures;
import me.blindle.core.GameEngine;
import me.blindle.core.model.Card;
import me.blindle.core.model.GameState;
import me.blindle.core.model.Hint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TrapLotsRuleTest {

    private static Card trap(String id) {
        return new Card(id, "Piège " + id, Set.of("trap"), Map.of("power", 5), "common", true,
                List.of(new Hint(1, "text", "Légende redoutée"), new Hint(2, "text", "Invaincu")));
    }

    private GameState started(GameEngine engine, long seed) {
        List<Card> cards = new ArrayList<>(Fixtures.deck(8));
        cards.add(trap("t1"));
        cards.add(trap("t2"));
        GameState s = engine.create("g", seed, Fixtures.settings(4), Fixtures.slots(), cards);
        for (String id : List.of("p1", "p2", "p3")) {
            s = engine.addPlayer(s, id, id, false).state();
        }
        return engine.start(s).state();
    }

    @Test
    void frequencyOnePutsTrapsFirst() {
        GameEngine engine = RulesSupport.engine(new TrapLotsRule(), Map.of("frequency", 1.0));
        List<Card> deck = started(engine, 5).deck();
        assertThat(deck.subList(0, 2)).allMatch(Card::trap);
        assertThat(deck.subList(2, 4)).noneMatch(Card::trap);
    }

    @Test
    void frequencyZeroKeepsTrapsOutOfPlayedRounds() {
        GameEngine engine = RulesSupport.engine(new TrapLotsRule(), Map.of("frequency", 0.0));
        List<Card> deck = started(engine, 5).deck();
        assertThat(deck.subList(0, 4)).noneMatch(Card::trap);
        assertThat(deck).hasSize(10);
    }

    @Test
    void sameSeedSameTrapPlacement() {
        GameEngine engine = RulesSupport.engine(new TrapLotsRule(), Map.of("frequency", 0.5));
        assertThat(started(engine, 9).deck()).isEqualTo(started(engine, 9).deck());
    }

    @Test
    void winnerOfATrapPaysThePenalty() {
        GameEngine engine = RulesSupport.engine(new TrapLotsRule(), Map.of("frequency", 1.0, "penalty", 80));
        GameState s = started(engine, 5);
        s = engine.revealFirstHint(engine.nextRound(s).state()).state();
        s = engine.openBidding(s, RulesSupport.DEADLINE).state();
        s = engine.placeBid(s, new me.blindle.core.model.Bid("p1", 100)).state();
        s = engine.revealCard(engine.closeBidding(s, RulesSupport.HIGHEST).state()).state();

        assertThat(s.player("p1").orElseThrow().budget()).isEqualTo(1000 - 100 - 80);
        assertThat(s.log()).anyMatch(l -> l.startsWith("Piège"));
    }

    @Test
    void requiresTrapCardsInTheme() {
        assertThat(new TrapLotsRule().requiredThemeFields()).containsExactly("isTrap");
    }
}
