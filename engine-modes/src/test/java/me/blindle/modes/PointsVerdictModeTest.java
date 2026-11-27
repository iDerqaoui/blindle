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

/** Équipes de 3 slots (Fixtures) : score = points des cartes − 25 par slot vide ; l'or restant ne compte pas. */
class PointsVerdictModeTest {

    private final PointsVerdictMode mode = new PointsVerdictMode();

    private static Card card(String id, String rarity) {
        return new Card(id, "Carte " + id, Set.of(), Map.of(), rarity, false,
                List.of(new Hint(1, "text", "vague"), new Hint(2, "initial", "C")));
    }

    private GameState lobby() {
        GameEngine engine = new GameEngine();
        List<Card> deck = List.of(card("leg", "legendary"), card("epi", "epic"), card("rar", "rare"),
                card("com", "common"), card("com2", "common"), card("epi2", "epic"), card("rar2", "rare"));
        GameState s = engine.create("g1", 7, Fixtures.settings(3), Fixtures.slots(), deck);
        s = engine.addPlayer(s, "p1", "Alice", false).state();
        s = engine.addPlayer(s, "p2", "Bilal", false).state();
        s = engine.addPlayer(s, "p3", "Chloé", false).state();
        return s.withPhase(Phase.VERDICT).withEachPlayer(p -> p.withBudget(1000));
    }

    private GameState verdict() {
        return lobby()
                // p1 : légendaire + commune = 70, 0 or, 1 slot vide → 70 − 25 = 45
                .withPlayer("p1", p -> p.withCardInSlot("captain", "leg").withCardInSlot("right", "com")
                        .withBudget(0))
                // p2 : épique + rare + commune = 65, 400 or (sans valeur) → 65
                .withPlayer("p2", p -> p.withCardInSlot("captain", "epi").withCardInSlot("right", "rar")
                        .withCardInSlot("support", "com2").withBudget(400));
        // p3 : rien, 1000 or, 3 slots vides → −75
    }

    @Test
    void scoreIsCardPointsMinusEmptySlots() {
        GameResult r = mode.compute(verdict(), List.of());

        assertThat(r.scores()).containsEntry("p1", 45).containsEntry("p2", 65).containsEntry("p3", -75);
        assertThat(r.ranking()).containsExactly("p2", "p1", "p3");
    }

    @Test
    void unspentGoldIsWorthNothing() {
        GameState s = verdict();
        GameState rich = s.withPlayer("p2", p -> p.withBudget(1000));
        GameState broke = s.withPlayer("p2", p -> p.withBudget(0));
        assertThat(PointsVerdictMode.score(rich, rich.player("p2").orElseThrow())).isEqualTo(65);
        assertThat(PointsVerdictMode.score(broke, broke.player("p2").orElseThrow())).isEqualTo(65);
    }

    @Test
    void hoardingDoesNotBeatAFullTeam() {
        GameState s = verdict();
        // garder tout son or sans acheter perd contre n'importe quelle équipe complète
        assertThat(PointsVerdictMode.score(s, s.player("p3").orElseThrow()))
                .isLessThan(PointsVerdictMode.score(s, s.player("p2").orElseThrow()));
    }

    @Test
    void tieIsBrokenByRuleBonusThenRemainingBudget() {
        // p1 : épique + rare + commune = 65 avec 410 or ; p2 : 65 avec 400 or
        GameState s = verdict().withPlayer("p1", p -> p.withoutSlot("captain").withoutSlot("right")
                .withCardInSlot("captain", "epi2").withCardInSlot("right", "rar2").withCardInSlot("support", "com")
                .withBudget(410));

        GameResult r = mode.compute(s, List.of());
        assertThat(r.scores()).containsEntry("p1", 65).containsEntry("p2", 65);
        // à égalité, le plus d'or restant gagne
        assertThat(r.ranking().subList(0, 2)).containsExactly("p1", "p2");
        // un bonus de règle passe avant l'or
        GameState bonus = s.withPlayer("p2", p -> p.withBonus(1));
        assertThat(mode.compute(bonus, List.of()).ranking().subList(0, 2)).containsExactly("p2", "p1");
        assertThat(mode.compute(s, List.of())).isEqualTo(r);
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
