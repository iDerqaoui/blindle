package me.blindle.modes;

import me.blindle.core.Fixtures;
import me.blindle.core.GameEngine;
import me.blindle.core.GameRandom;
import me.blindle.core.model.Bid;
import me.blindle.core.model.GameState;
import me.blindle.core.model.LotResult;
import me.blindle.core.model.LotState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SealedBidModeTest {

    private final SealedBidMode mode = new SealedBidMode();
    private final GameEngine engine = new GameEngine();

    private GameState bidding() {
        GameState s = engine.start(Fixtures.lobby(engine, 1, 3)).state();
        s = engine.revealFirstHint(engine.nextRound(s).state()).state();
        return engine.openBidding(s, Instant.parse("2026-01-01T00:00:30Z")).state();
    }

    @Test
    void highestBidWinsAndPaysItsBid() {
        LotState lot = bidding().lot()
                .withBid(new Bid("p1", 100)).withBid(new Bid("p2", 250)).withBid(new Bid("p3", 180));

        assertThat(mode.resolve(lot, GameRandom.of(1))).contains(new LotResult("p2", 250));
    }

    @Test
    void noBidMeansNoWinner() {
        assertThat(mode.resolve(bidding().lot(), GameRandom.of(1))).isEmpty();
    }

    @Test
    void tieIsBrokenBySeedDeterministically() {
        LotState lot = bidding().lot().withBid(new Bid("p1", 200)).withBid(new Bid("p2", 200));

        Optional<LotResult> first = mode.resolve(lot, GameRandom.derive(9, "tie"));
        assertThat(mode.resolve(lot, GameRandom.derive(9, "tie"))).isEqualTo(first);

        Set<String> winners = new HashSet<>();
        for (long seed = 0; seed < 20; seed++) {
            winners.add(mode.resolve(lot, GameRandom.derive(seed, "tie")).orElseThrow().winnerId());
        }
        assertThat(winners).containsExactlyInAnyOrder("p1", "p2");
    }

    @Test
    void completeOnceEveryEligiblePlayerBidOrPassed() {
        GameState s = bidding();
        s = engine.placeBid(s, new Bid("p1", 100)).state();
        s = engine.pass(s, "p2").state();
        assertThat(mode.isComplete(s)).isFalse();

        s = engine.placeBid(s, new Bid("p3", 120)).state();
        assertThat(mode.isComplete(s)).isTrue();
    }

    @Test
    void bidsAreHidden() {
        assertThat(mode.bidsVisible()).isFalse();
    }
}
