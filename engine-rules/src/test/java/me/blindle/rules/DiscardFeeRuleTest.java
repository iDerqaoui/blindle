package me.blindle.rules;

import me.blindle.core.Fixtures;
import me.blindle.core.GameEngine;
import me.blindle.core.event.GameEvent;
import me.blindle.core.model.GameState;
import me.blindle.core.rule.RuleParams;
import me.blindle.core.rule.RuleResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscardFeeRuleTest {

    private final DiscardFeeRule rule = new DiscardFeeRule();
    private final GameEngine plain = new GameEngine();

    private GameState started() {
        return plain.start(Fixtures.lobby(plain, 1, 3)).state(); // budget 1000 chacun
    }

    private static GameState applied(RuleResult r) {
        return ((RuleResult.Continue) r).state();
    }

    @Test
    void sellerPaysTheFeeWhenNobodyBuys() {
        GameState s = applied(rule.apply(new GameEvent.LotDiscarded(1, "p1"), started(), RuleParams.EMPTY));

        assertThat(s.player("p1").orElseThrow().budget()).isEqualTo(850);
        assertThat(s.player("p2").orElseThrow().budget()).isEqualTo(1000);
    }

    @Test
    void feeIsConfigurableAndCappedByBudget() {
        GameState poor = started().withPlayer("p1", p -> p.withBudget(30));
        GameState s = applied(rule.apply(new GameEvent.LotDiscarded(1, "p1"), poor,
                RuleParams.of(Map.of("amount", 80))));

        assertThat(s.player("p1").orElseThrow().budget()).isZero();
    }

    @Test
    void noSellerNoFee() {
        GameState before = started();
        assertThat(rule.apply(new GameEvent.LotDiscarded(1, null), before, RuleParams.EMPTY))
                .isEqualTo(RuleResult.ok(before));
    }

    @Test
    void invalidAmountIsRefused() {
        assertThatThrownBy(() -> rule.validateParams(RuleParams.of(Map.of("amount", -5))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
