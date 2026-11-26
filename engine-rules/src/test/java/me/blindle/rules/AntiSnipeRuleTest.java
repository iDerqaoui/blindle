package me.blindle.rules;

import me.blindle.core.GameEngine;
import me.blindle.core.model.Bid;
import me.blindle.core.model.GameState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static me.blindle.rules.RulesSupport.DEADLINE;
import static org.assertj.core.api.Assertions.assertThat;

class AntiSnipeRuleTest {

    private final GameEngine engine = RulesSupport.engine(new AntiSnipeRule(), Map.of("extendSeconds", 5));

    @Test
    void lastSecondBidExtendsDeadline() {
        GameState s = RulesSupport.bidding(engine, 1);
        GameState after = engine.placeBid(s, new Bid("p1", 100), DEADLINE.minusSeconds(2)).state();
        assertThat(after.lot().deadline()).isEqualTo(DEADLINE.plusSeconds(3));
    }

    @Test
    void earlyBidKeepsDeadline() {
        GameState s = RulesSupport.bidding(engine, 1);
        GameState after = engine.placeBid(s, new Bid("p1", 100), DEADLINE.minusSeconds(20)).state();
        assertThat(after.lot().deadline()).isEqualTo(DEADLINE);
    }

    @Test
    void unknownReceptionTimeIsIgnored() {
        GameState s = RulesSupport.bidding(engine, 1);
        assertThat(engine.placeBid(s, new Bid("p1", 100)).state().lot().deadline()).isEqualTo(DEADLINE);
    }

    @Test
    void onlyForVisibleBidModes() {
        assertThat(new AntiSnipeRule().requiredBidModes()).containsExactlyInAnyOrder("open", "drawer");
    }
}
