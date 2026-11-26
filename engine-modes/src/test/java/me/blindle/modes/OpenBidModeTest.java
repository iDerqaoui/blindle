package me.blindle.modes;

import me.blindle.core.Fixtures;
import me.blindle.core.GameEngine;
import me.blindle.core.GameRandom;
import me.blindle.core.model.Bid;
import me.blindle.core.model.GameState;
import me.blindle.core.model.LotResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenBidModeTest {

    private final OpenBidMode mode = new OpenBidMode();
    private final GameEngine engine = new GameEngine();

    private GameState bidding() {
        GameState s = engine.start(Fixtures.lobby(engine, 1, 3)).state();
        s = engine.revealFirstHint(engine.nextRound(s).state()).state();
        return engine.openBidding(s, Instant.parse("2026-01-01T00:00:30Z")).state();
    }

    @Test
    void eachBidMustBeatTheLeader() {
        GameState s = engine.placeBid(bidding(), new Bid("p1", 100)).state();

        assertThatThrownBy(() -> mode.validateBid(s, new Bid("p2", 100)))
                .hasFieldOrPropertyWithValue("code", "BID_TOO_LOW");
        mode.validateBid(s, new Bid("p2", 110));
    }

    @Test
    void leaderCannotPass() {
        GameState s = engine.placeBid(bidding(), new Bid("p1", 100)).state();
        assertThatThrownBy(() -> mode.validatePass(s, "p1")).hasFieldOrPropertyWithValue("code", "LEADER_CANNOT_PASS");
        mode.validatePass(s, "p2");
    }

    @Test
    void completeWhenEveryoneElsePassed() {
        GameState s = engine.placeBid(bidding(), new Bid("p1", 100)).state();
        s = engine.placeBid(s, new Bid("p2", 150)).state();
        s = engine.pass(s, "p3").state();
        assertThat(mode.isComplete(s)).isFalse(); // p1 n'a pas renoncé

        s = engine.pass(s, "p1").state();
        assertThat(mode.isComplete(s)).isTrue();
        assertThat(mode.resolve(s.lot(), GameRandom.of(1))).contains(new LotResult("p2", 150));
    }

    @Test
    void completeWhenAllPassWithoutBid() {
        GameState s = bidding();
        for (String id : s.eligibleBidders()) {
            s = engine.pass(s, id).state();
        }
        assertThat(mode.isComplete(s)).isTrue();
        assertThat(mode.resolve(s.lot(), GameRandom.of(1))).isEmpty();
    }

    @Test
    void bidsAreVisible() {
        assertThat(mode.bidsVisible()).isTrue();
    }
}
