package me.blindle.modes;

import me.blindle.core.Fixtures;
import me.blindle.core.GameEngine;
import me.blindle.core.Phase;
import me.blindle.core.model.Card;
import me.blindle.core.model.GameResult;
import me.blindle.core.model.GameState;
import me.blindle.core.model.Hint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PointsVerdictModeTest {

    private final PointsVerdictMode mode = new PointsVerdictMode();

    private static Card card(String id, String rarity) {
        return new Card(id, "Carte " + id, Set.of(), Map.of(), rarity, false,
                List.of(new Hint(1, "text", "vague"), new Hint(2, "initial", "C")));
    }

    private GameState verdict() {
        GameEngine engine = new GameEngine();
        List<Card> deck = List.of(card("leg", "legendary"), card("epi", "epic"), card("rar", "rare"),
                card("com", "common"), card("com2", "common"), card("epi2", "epic"));
        GameState s = engine.create("g1", 7, Fixtures.settings(3), Fixtures.slots(), deck);
        s = engine.addPlayer(s, "p1", "Alice", false).state();
        s = engine.addPlayer(s, "p2", "Bilal", false).state();
        s = engine.addPlayer(s, "p3", "Chloé", false).state();
        return s.withPhase(Phase.VERDICT)
                // p1 : légendaire + commune = 70, a tout dépensé
                .withPlayer("p1", p -> p.withCardInSlot("captain", "leg").withCardInSlot("right", "com")
                        .withBudget(0))
                // p2 : épique + rare + commune = 65
                .withPlayer("p2", p -> p.withCardInSlot("captain", "epi").withCardInSlot("right", "rar")
                        .withCardInSlot("support", "com2").withBudget(400));
        // p3 : rien → 0
    }

    @Test
    void scoreIsSumOfRarityPointsRegardlessOfCoins() {
        GameResult r = mode.compute(verdict(), List.of());

        assertThat(r.scores()).containsEntry("p1", 70).containsEntry("p2", 65).containsEntry("p3", 0);
        assertThat(r.ranking()).containsExactly("p1", "p2", "p3");
    }

    @Test
    void tieIsBrokenByRuleBonusThenRemainingBudget() {
        // p1 : 70 ; p2 : épique + épique = 70, avec 400 pièces restantes
        GameState s = verdict().withPlayer("p2", p -> p.withCardInSlot("right", "epi2").withoutSlot("support"));

        assertThat(mode.compute(s, List.of()).scores()).containsEntry("p2", 70);
        // à égalité, le plus économe gagne
        assertThat(mode.compute(s, List.of()).ranking().subList(0, 2)).containsExactly("p2", "p1");
        // un bonus de règle passe avant le budget
        GameState bonus = s.withPlayer("p1", p -> p.withBonus(1));
        assertThat(mode.compute(bonus, List.of()).ranking().subList(0, 2)).containsExactly("p1", "p2");
        assertThat(mode.compute(s, List.of())).isEqualTo(mode.compute(s, List.of()));
    }

    @Test
    void requiresRarity() {
        assertThat(mode.supportsTheme(Set.of("rarity")::contains)).isTrue();
        assertThat(mode.supportsTheme(Set.of("stats")::contains)).isFalse();
        assertThat(mode.usesVotes()).isFalse();
    }

    @Test
    void rarityPointsScale() {
        assertThat(RarityPoints.of(card("a", "common"))).isEqualTo(10);
        assertThat(RarityPoints.of(card("a", "rare"))).isEqualTo(20);
        assertThat(RarityPoints.of(card("a", "epic"))).isEqualTo(35);
        assertThat(RarityPoints.of(card("a", "legendary"))).isEqualTo(60);
        assertThat(RarityPoints.of(card("a", null))).isZero();
        // une équipe régulière bat une star entourée de communes
        assertThat(5 * RarityPoints.of(card("a", "epic")))
                .isGreaterThan(RarityPoints.of(card("a", "legendary")) + 4 * RarityPoints.of(card("a", "common")));
    }
}
