package me.blindle.modes;

import me.blindle.core.Fixtures;
import me.blindle.core.model.Card;
import me.blindle.core.model.Hint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CardValuationTest {

    private static Card card(Map<String, Integer> stats, String rarity) {
        return new Card("x", "X", Set.of(), stats, rarity, false,
                List.of(new Hint(1, "text", "a"), new Hint(2, "text", "b")));
    }

    @Test
    void twiceTheAverageStatPlusRarityBonus() {
        assertThat(CardValuation.of(card(Map.of("a", 90, "b", 70), "legendary"))).isEqualTo(160 + 150);
        assertThat(CardValuation.of(card(Map.of("a", 30), "common"))).isEqualTo(60 + 25);
        assertThat(CardValuation.of(Fixtures.card("c1"))).isEqualTo(125);
    }

    @Test
    void missingFieldsFallBackToNeutralValues() {
        assertThat(CardValuation.of(card(Map.of(), "epic"))).isEqualTo(100 + 100);
        assertThat(CardValuation.of(card(Map.of("a", 40), null))).isEqualTo(80 + 50);
        assertThat(CardValuation.of(card(null, null))).isEqualTo(150);
    }

    @Test
    void lotValueIsTheSumOfItsCards() {
        assertThat(CardValuation.sum(List.of(Fixtures.card("c1"), Fixtures.card("c2")))).isEqualTo(250);
    }
}
