package me.blindle.rules;

import me.blindle.core.BidResolver;
import me.blindle.core.Fixtures;
import me.blindle.core.GameEngine;
import me.blindle.core.GameException;
import me.blindle.core.event.GameEvent;
import me.blindle.core.model.Bid;
import me.blindle.core.model.GameState;
import me.blindle.core.rule.RuleEngine;
import me.blindle.core.rule.RuleParams;
import me.blindle.core.rule.RuleResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RisingFloorRuleTest {

    private static final Instant DEADLINE = Instant.parse("2026-01-01T00:00:30Z");
    private final RisingFloorRule rule = new RisingFloorRule();
    private final RuleParams params = RuleParams.of(Map.of("base", 50, "stepPerHint", 50));

    private GameState drawn() {
        GameEngine plain = new GameEngine();
        GameState s = plain.start(Fixtures.lobby(plain, 1, 3)).state();
        return plain.nextRound(s).state();
    }

    @Test
    void floorRisesWithEachHint() {
        GameState s = drawn();
        int[] expected = {50, 100, 150};
        for (int level = 1; level <= 3; level++) {
            RuleResult r = rule.apply(new GameEvent.HintRevealed(1, level), s, params);
            assertThat(((RuleResult.Continue) r).state().lot().floorPrice()).isEqualTo(expected[level - 1]);
        }
    }

    @Test
    void defaultsAndValidationOfParams() {
        assertThat(RisingFloorRule.Params.from(RuleParams.EMPTY)).isEqualTo(new RisingFloorRule.Params(50, 50));
        assertThatThrownBy(() -> RisingFloorRule.Params.from(RuleParams.of(Map.of("base", -1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void engineAppliesFloorAndRefusesLowerBids() {
        GameEngine engine = new GameEngine(new RuleEngine(List.of(new RuleEngine.ActiveRule(rule, params))));
        GameState s = engine.start(Fixtures.lobby(engine, 1, 3)).state();
        s = engine.revealFirstHint(engine.nextRound(s).state()).state();
        assertThat(s.lot().floorPrice()).isEqualTo(50);

        s = engine.openBidding(s, DEADLINE).state();
        BidResolver nobody = (lot, random) -> Optional.empty();
        s = engine.openBidding(engine.closeBidding(s, nobody).state(), DEADLINE).state();
        assertThat(s.lot().hintLevel()).isEqualTo(2);
        assertThat(s.lot().floorPrice()).isEqualTo(100);

        GameState bidding = s;
        assertThatThrownBy(() -> engine.placeBid(bidding, new Bid("p1", 80)))
                .isInstanceOf(GameException.class)
                .hasFieldOrPropertyWithValue("code", "BID_BELOW_FLOOR");
        assertThat(engine.placeBid(bidding, new Bid("p1", 100)).state().lot().bids()).hasSize(1);
    }
}
