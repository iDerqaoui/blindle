package me.blindle.rules;

import me.blindle.core.BidResolver;
import me.blindle.core.GameEngine;
import me.blindle.core.model.GameState;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static me.blindle.rules.RulesSupport.DEADLINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JokerSpyRuleTest {

    private final GameEngine engine = RulesSupport.engine(new JokerSpyRule(), Map.of("usesPerGame", 1));

    @Test
    void everyPlayerGetsJokersAtStart() {
        GameState s = RulesSupport.bidding(engine, 1);
        assertThat(s.players()).allMatch(p -> p.jokersLeft("spy") == 1);
    }

    @Test
    void revealsNextHintPrivatelyAndConsumesJoker() {
        GameState s = RulesSupport.bidding(engine, 1);
        var nextHint = s.lot().cards().get(0).hint(2).orElseThrow();

        GameState after = engine.useJoker(s, "p1", "spy", null).state();

        assertThat(after.player("p1").orElseThrow().privateHints()).containsExactly(nextHint);
        assertThat(after.player("p2").orElseThrow().privateHints()).isEmpty();
        assertThat(after.player("p1").orElseThrow().jokersLeft("spy")).isZero();
        assertThat(after.lot().hintLevel()).isEqualTo(1);
        assertThatThrownBy(() -> engine.useJoker(after, "p1", "spy", null))
                .hasFieldOrPropertyWithValue("code", "NO_JOKER");
    }

    @Test
    void refusedOnLastHintWithoutConsumingJoker() {
        BidResolver nobody = (lot, random) -> Optional.empty();
        GameState s = RulesSupport.bidding(engine, 1);
        s = engine.openBidding(engine.closeBidding(s, nobody).state(), DEADLINE).state();
        GameState last = engine.openBidding(engine.closeBidding(s, nobody).state(), DEADLINE).state();

        assertThatThrownBy(() -> engine.useJoker(last, "p1", "spy", null))
                .hasFieldOrPropertyWithValue("code", "NO_MORE_HINTS");
        assertThat(last.player("p1").orElseThrow().jokersLeft("spy")).isEqualTo(1);
    }

    @Test
    void privateHintsAreClearedAtRoundEnd() {
        GameState s = engine.useJoker(RulesSupport.bidding(engine, 1), "p1", "spy", null).state();
        s = RulesSupport.win(engine, s, "p2", 100, "captain");
        s = engine.nextRound(s).state();

        assertThat(s.player("p1").orElseThrow().privateHints()).isEmpty();
    }
}
