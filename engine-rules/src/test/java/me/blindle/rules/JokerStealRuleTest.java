package me.blindle.rules;

import me.blindle.core.GameEngine;
import me.blindle.core.model.GameState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JokerStealRuleTest {

    private final GameEngine engine = RulesSupport.engine(new JokerStealRule(),
            Map.of("multiplier", 1.5, "usesPerGame", 1));

    /** p1 a acheté une carte 100 pièces (slot captain) ; le round 2 est ouvert. */
    private GameState afterP1Bought() {
        GameState s = RulesSupport.win(engine, RulesSupport.bidding(engine, 3), "p1", 100, "captain");
        return RulesSupport.nextBidding(engine, s);
    }

    @Test
    void buysOpponentCardAtMultipliedPrice() {
        GameState s = afterP1Bought();
        String cardId = s.player("p1").orElseThrow().team().get("captain");

        GameState after = engine.useJoker(s, "p2", "steal", "p1/captain").state();

        var thief = after.player("p2").orElseThrow();
        var victim = after.player("p1").orElseThrow();
        assertThat(thief.team()).containsEntry("captain", cardId);
        assertThat(thief.budget()).isEqualTo(1000 - 150);
        assertThat(thief.paid()).containsEntry(cardId, 150);
        assertThat(victim.team()).doesNotContainKey("captain");
        assertThat(victim.budget()).isEqualTo(900 + 150);
        assertThat(after.log()).anyMatch(l -> l.contains("a volé"));
    }

    @Test
    void goesToAnotherFreeSlotWhenSameIsTaken() {
        GameState s = RulesSupport.win(engine, afterP1Bought(), "p2", 60, "captain");
        s = RulesSupport.nextBidding(engine, s);

        GameState after = engine.useJoker(s, "p2", "steal", "p1/captain").state();

        assertThat(after.player("p2").orElseThrow().team()).hasSize(2).containsKey("right");
    }

    @Test
    void invalidTargetsAreRejected() {
        GameState s = afterP1Bought();
        assertThatThrownBy(() -> engine.useJoker(s, "p2", "steal", null)).hasFieldOrPropertyWithValue("code", "BAD_TARGET");
        assertThatThrownBy(() -> engine.useJoker(s, "p2", "steal", "p1/right")).hasFieldOrPropertyWithValue("code", "BAD_TARGET");
        assertThatThrownBy(() -> engine.useJoker(s, "p1", "steal", "p1/captain")).hasFieldOrPropertyWithValue("code", "BAD_TARGET");
    }

    @Test
    void thiefMustAffordThePrice() {
        GameState s = afterP1Bought().withPlayer("p2", p -> p.withBudget(100));
        assertThatThrownBy(() -> engine.useJoker(s, "p2", "steal", "p1/captain"))
                .hasFieldOrPropertyWithValue("code", "OVER_BUDGET");
    }
}
