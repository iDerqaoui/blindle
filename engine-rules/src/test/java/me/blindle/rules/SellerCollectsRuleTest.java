package me.blindle.rules;

import me.blindle.core.Fixtures;
import me.blindle.core.GameEngine;
import me.blindle.core.event.GameEvent;
import me.blindle.core.model.GameState;
import me.blindle.core.rule.RuleParams;
import me.blindle.core.rule.RuleResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SellerCollectsRuleTest {

    private final SellerCollectsRule rule = new SellerCollectsRule();
    private final GameEngine plain = new GameEngine();

    /** Partie démarrée, p2 vient de payer 300 (budget 700). */
    private GameState afterSale() {
        GameState s = plain.start(Fixtures.lobby(plain, 1, 3)).state();
        return s.withPlayer("p2", p -> p.withBudget(700));
    }

    private static GameState applied(RuleResult r) {
        return ((RuleResult.Continue) r).state();
    }

    @Test
    void sellerCollectsThePrice() {
        GameState s = applied(rule.apply(new GameEvent.LotWon("p2", 300, "p1"), afterSale(), RuleParams.EMPTY));

        assertThat(s.player("p1").orElseThrow().budget()).isEqualTo(1300);
        assertThat(s.player("p2").orElseThrow().budget()).isEqualTo(700);
        assertThat(s.player("p3").orElseThrow().budget()).isEqualTo(1000);
    }

    @Test
    void sellerBuyingOwnLotPaysTheBank() {
        GameState before = afterSale();
        assertThat(rule.apply(new GameEvent.LotWon("p1", 300, "p1"), before, RuleParams.EMPTY))
                .isEqualTo(RuleResult.ok(before));
    }

    @Test
    void noSellerMeansTheBankSells() {
        GameState before = afterSale();
        assertThat(rule.apply(new GameEvent.LotWon("p2", 300), before, RuleParams.EMPTY))
                .isEqualTo(RuleResult.ok(before));
        assertThat(rule.apply(new GameEvent.GameStarted(), before, RuleParams.EMPTY))
                .isEqualTo(RuleResult.ok(before));
    }
}
