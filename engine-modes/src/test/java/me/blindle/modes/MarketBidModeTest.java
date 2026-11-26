package me.blindle.modes;

import me.blindle.core.Fixtures;
import me.blindle.core.GameEngine;
import me.blindle.core.GameRandom;
import me.blindle.core.model.Bid;
import me.blindle.core.model.GameState;
import me.blindle.core.model.LotState;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Cartes de test : valeur 125 (stat 50, common), trois paliers. */
class MarketBidModeTest {

    private final MarketBidMode mode = new MarketBidMode();
    private final GameEngine engine = new GameEngine();

    private GameState bidding() {
        GameState s = engine.start(Fixtures.lobby(engine, 1, 3)).state();
        s = engine.revealFirstHint(engine.nextRound(s).state()).state();
        return engine.openBidding(s, Instant.parse("2026-01-01T00:00:30Z")).state();
    }

    private static int market(LotState lot, long seed) {
        return MarketBidMode.marketPrice(lot, GameRandom.derive(seed, "market"));
    }

    @Test
    void marketPriceRisesWithHintsAndStaysWithinNoise() {
        LotState lot = bidding().lot();
        for (long seed = 0; seed < 50; seed++) {
            int first = market(lot.withHintLevel(1), seed);
            int last = market(lot.withHintLevel(3), seed);
            // 125 × 0,5 × [0,85 ; 1,15] puis 125 × 0,9 × [0,85 ; 1,15]
            assertThat(first).isBetween(53, 72);
            assertThat(last).isBetween(95, 130);
            assertThat(last).isGreaterThan(first);
        }
    }

    @Test
    void marketPriceIsDeterministicAndNeverBelowFloor() {
        LotState lot = bidding().lot().withHintLevel(1);
        assertThat(market(lot, 7)).isEqualTo(market(lot, 7));
        assertThat(market(lot.withFloorPrice(500), 7)).isEqualTo(500);
    }

    @Test
    void bidReachingTheMarketWinsAndPaysItsOwnBid() {
        LotState lot = bidding().lot().withHintLevel(1);
        int price = market(lot, 3);

        assertThat(mode.resolve(lot.withBid(new Bid("p1", price)), GameRandom.derive(3, "market")))
                .hasValueSatisfying(r -> {
                    assertThat(r.winnerId()).isEqualTo("p1");
                    assertThat(r.price()).isEqualTo(price);
                });
        assertThat(mode.resolve(lot.withBid(new Bid("p1", price - 1)), GameRandom.derive(3, "market")))
                .isEmpty();
    }

    @Test
    void highestBidAboveTheMarketWins() {
        LotState lot = bidding().lot().withHintLevel(1)
                .withBid(new Bid("p1", 200)).withBid(new Bid("p2", 300)).withBid(new Bid("p3", 10));

        assertThat(mode.resolve(lot, GameRandom.of(1))).hasValueSatisfying(r -> {
            assertThat(r.winnerId()).isEqualTo("p2");
            assertThat(r.price()).isEqualTo(300);
        });
    }

    @Test
    void noBidMeansNoWinner() {
        assertThat(mode.resolve(bidding().lot(), GameRandom.of(1))).isEmpty();
    }

    @Test
    void completeOnceEveryEligiblePlayerActedAndBidsStayHidden() {
        GameState s = bidding();
        s = engine.placeBid(s, new Bid("p1", 100)).state();
        s = engine.pass(s, "p2").state();
        assertThat(mode.isComplete(s)).isFalse();
        s = engine.pass(s, "p3").state();
        assertThat(mode.isComplete(s)).isTrue();
        assertThat(mode.bidsVisible()).isFalse();
    }
}
