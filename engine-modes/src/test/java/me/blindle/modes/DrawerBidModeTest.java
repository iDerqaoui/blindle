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

/** Mode « Blind Bid » : le piocheur ouvre, les autres surenchérissent à l'aveugle ou laissent. */
class DrawerBidModeTest {

    private final DrawerBidMode mode = new DrawerBidMode();
    private final GameEngine engine = new GameEngine();

    /** Enchère ouverte au round {@code round}, trois joueurs p1, p2, p3. */
    private GameState bidding(int round) {
        GameState s = engine.start(Fixtures.lobby(engine, 1, 10)).state();
        s = engine.revealFirstHint(engine.nextRound(s).state()).state();
        return engine.openBidding(s, Instant.parse("2026-01-01T00:00:30Z")).state().withRoundIndex(round);
    }

    private GameState bid(GameState s, String player, int amount) {
        mode.validateBid(s, new Bid(player, amount));
        return engine.placeBid(s, new Bid(player, amount)).state();
    }

    private GameState pass(GameState s, String player) {
        mode.validatePass(s, player);
        return engine.pass(s, player).state();
    }

    @Test
    void drawerRotatesEachRound() {
        assertThat(mode.drawerId(bidding(1))).isEqualTo("p1");
        assertThat(mode.drawerId(bidding(2))).isEqualTo("p2");
        assertThat(mode.drawerId(bidding(3))).isEqualTo("p3");
        assertThat(mode.drawerId(bidding(4))).isEqualTo("p1");
    }

    @Test
    void drawerWithFullTeamIsSkipped() {
        GameState s = bidding(1).withPlayer("p1", p -> p.withCardInSlot("captain", "x")
                .withCardInSlot("right", "y").withCardInSlot("support", "z"));
        assertThat(mode.drawerId(s)).isEqualTo("p2");
    }

    @Test
    void othersWaitForTheDrawerToOpen() {
        GameState s = bidding(1);
        assertThatThrownBy(() -> mode.validateBid(s, new Bid("p2", 100)))
                .hasFieldOrPropertyWithValue("code", "DRAWER_FIRST");
        assertThatThrownBy(() -> mode.validatePass(s, "p2"))
                .hasFieldOrPropertyWithValue("code", "DRAWER_FIRST");
    }

    @Test
    void overbidOrLeaveThenHighestWins() {
        GameState s = bid(bidding(1), "p1", 100);
        assertThat(mode.isComplete(s)).isFalse();
        GameState after = s;
        assertThatThrownBy(() -> mode.validateBid(after, new Bid("p2", 100)))
                .hasFieldOrPropertyWithValue("code", "BID_TOO_LOW");
        s = bid(s, "p2", 150);
        assertThatThrownBy(() -> mode.validatePass(bid(after, "p2", 150), "p2"))
                .hasFieldOrPropertyWithValue("code", "LEADER_CANNOT_PASS");
        s = pass(s, "p1");
        assertThat(mode.isComplete(s)).isFalse();
        s = pass(s, "p3");
        assertThat(mode.isComplete(s)).isTrue();
        assertThat(mode.resolve(s.lot(), GameRandom.of(1))).contains(new LotResult("p2", 150));
    }

    @Test
    void drawerKeepsTheCardWhenEveryoneLeaves() {
        GameState s = pass(pass(bid(bidding(1), "p1", 80), "p2"), "p3");
        assertThat(mode.isComplete(s)).isTrue();
        assertThat(mode.resolve(s.lot(), GameRandom.of(1))).contains(new LotResult("p1", 80));
    }

    @Test
    void nobodyWantsItThenItGoesToTheTrash() {
        GameState s = pass(pass(pass(bidding(1), "p1"), "p2"), "p3");
        assertThat(mode.isComplete(s)).isTrue();
        assertThat(mode.resolve(s.lot(), GameRandom.of(1))).isEmpty();

        // Avec un seul palier d'indice, le lot invendu est défaussé : la poubelle, publique.
        GameState single = s.withSettings(new me.blindle.core.model.GameSettings(1000, 10, 3, 1, 30));
        GameState next = engine.closeBidding(single, mode).state();
        assertThat(next.discarded()).hasSize(1);
        assertThat(mode.autoPlace()).isTrue();
        assertThat(mode.bidsVisible()).isTrue();
    }
}
