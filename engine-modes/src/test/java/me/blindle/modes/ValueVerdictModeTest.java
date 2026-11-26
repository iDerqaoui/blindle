package me.blindle.modes;

import me.blindle.core.Fixtures;
import me.blindle.core.GameEngine;
import me.blindle.core.Phase;
import me.blindle.core.model.GameResult;
import me.blindle.core.model.GameState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Cartes de test : valeur 125 chacune ; budget de départ 1000. */
class ValueVerdictModeTest {

    private final ValueVerdictMode mode = new ValueVerdictMode();

    private GameState verdict() {
        GameEngine engine = new GameEngine();
        GameState s = engine.start(Fixtures.lobby(engine, 5, 3)).state();
        return s.withPhase(Phase.VERDICT)
                // p1 : deux cartes pour 100 au total → 250 − 100 = 150
                .withPlayer("p1", p -> p.withCardInSlot("captain", "c1").withCardInSlot("right", "c2")
                        .withBudget(900))
                // p2 : une carte payée 200 → 125 − 200 = −75
                .withPlayer("p2", p -> p.withCardInSlot("captain", "c3").withBudget(800));
        // p3 : rien acheté → 0
    }

    @Test
    void scoreIsTeamValueMinusCoinsSpent() {
        GameResult r = mode.compute(verdict(), List.of());

        assertThat(r.scores()).containsEntry("p1", 150).containsEntry("p2", -75).containsEntry("p3", 0);
        assertThat(r.ranking()).containsExactly("p1", "p3", "p2");
    }

    @Test
    void tieIsBrokenByRuleBonusThenDeterministic() {
        GameState s = verdict().withPlayer("p1", p -> p.withBudget(1000).withCardInSlot("right", "c2"))
                .withPlayer("p2", p -> p.withBudget(1000).withCardInSlot("right", "c4").withBonus(1));
        // p1 : 250, p2 : 250 + bonus de synergie
        GameResult r = mode.compute(s, List.of());

        assertThat(r.ranking().subList(0, 2)).containsExactly("p2", "p1");
        assertThat(mode.compute(s, List.of())).isEqualTo(r);
    }

    @Test
    void requiresStatsOrRarity() {
        assertThat(mode.supportsTheme(Set.of("stats")::contains)).isTrue();
        assertThat(mode.supportsTheme(Set.of("rarity")::contains)).isTrue();
        assertThat(mode.supportsTheme(Set.of("tags")::contains)).isFalse();
        assertThat(mode.usesVotes()).isFalse();
    }
}
