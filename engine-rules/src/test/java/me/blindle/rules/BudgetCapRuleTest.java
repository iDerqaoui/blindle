package me.blindle.rules;

import me.blindle.core.Fixtures;
import me.blindle.core.GameEngine;
import me.blindle.core.event.GameEvent;
import me.blindle.core.model.Bid;
import me.blindle.core.model.GameState;
import me.blindle.core.rule.RuleEngine;
import me.blindle.core.rule.RuleParams;
import me.blindle.core.rule.RuleRejectedException;
import me.blindle.core.rule.RuleResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BudgetCapRuleTest {

    private final BudgetCapRule rule = new BudgetCapRule();
    private final GameEngine plain = new GameEngine();

    private GameState started() {
        return plain.start(Fixtures.lobby(plain, 1, 3)).state(); // budget 1000 chacun
    }

    @Test
    void bidWithinBudgetPasses() {
        GameState s = started();
        RuleResult result = rule.apply(new GameEvent.BidPlaced(new Bid("p1", 1000)), s, RuleParams.EMPTY);

        assertThat(result).isEqualTo(RuleResult.ok(s));
    }

    @Test
    void bidOverBudgetIsRejected() {
        GameState s = started();
        RuleResult result = rule.apply(new GameEvent.BidPlaced(new Bid("p1", 1001)), s, RuleParams.EMPTY);

        assertThat(result).isInstanceOf(RuleResult.Reject.class);
        assertThat(((RuleResult.Reject) result).code()).isEqualTo("OVER_BUDGET");
    }

    @Test
    void otherEventsAreIgnored() {
        GameState s = started();
        assertThat(rule.apply(new GameEvent.GameStarted(), s, RuleParams.EMPTY)).isEqualTo(RuleResult.ok(s));
    }

    @Test
    void engineRefusesOverBudgetBidAndKeepsState() {
        GameEngine engine = new GameEngine(new RuleEngine(
                List.of(new RuleEngine.ActiveRule(rule, RuleParams.EMPTY))));
        GameState s = engine.start(Fixtures.lobby(engine, 1, 3)).state();
        GameState bidding = engine.openBidding(
                engine.revealFirstHint(engine.nextRound(s).state()).state(),
                Instant.parse("2026-01-01T00:00:30Z")).state();

        assertThatThrownBy(() -> engine.placeBid(bidding, new Bid("p1", 1500)))
                .isInstanceOf(RuleRejectedException.class)
                .hasFieldOrPropertyWithValue("code", "OVER_BUDGET");
        assertThat(bidding.lot().bids()).isEmpty();
        assertThat(engine.placeBid(bidding, new Bid("p1", 900)).state().lot().bids()).hasSize(1);
    }
}
