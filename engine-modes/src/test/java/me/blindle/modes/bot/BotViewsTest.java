package me.blindle.modes.bot;

import me.blindle.core.Fixtures;
import me.blindle.core.GameEngine;
import me.blindle.core.model.Bid;
import me.blindle.core.model.Card;
import me.blindle.core.model.GameState;
import me.blindle.modes.OpenBidMode;
import me.blindle.modes.SealedBidMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** La vue d'un bot est filtrée comme celle d'un humain (principe 2). */
class BotViewsTest {

    private final GameEngine engine = new GameEngine();

    private GameState bidding() {
        GameState s = engine.create("g", 3, Fixtures.settings(3), Fixtures.slots(), Fixtures.deck(10));
        s = engine.addPlayer(s, "p1", "Alice", false).state();
        s = engine.addPlayer(s, "bot-1", "Bot", true).state();
        s = engine.start(s).state();
        s = engine.revealFirstHint(engine.nextRound(s).state()).state();
        s = engine.openBidding(s, Instant.parse("2026-01-01T00:00:30Z")).state();
        return engine.placeBid(s, new Bid("p1", 120)).state();
    }

    @Test
    void sealedViewHidesOtherBidsAndLaterHints() {
        BotView v = BotViews.of(bidding(), "bot-1", new SealedBidMode());

        assertThat(v.visibleBids()).isEmpty();
        assertThat(v.hints()).hasSize(1).allSatisfy(h -> assertThat(h.level()).isEqualTo(1));
        assertThat(v.hintLevel()).isEqualTo(1);
        assertThat(v.maxHintLevel()).isEqualTo(3);
        assertThat(v.budget()).isEqualTo(1000);
        assertThat(v.freeSlots()).isEqualTo(3);
        assertThat(v.botSeed()).isNotEqualTo(3L);
    }

    @Test
    void openViewShowsOtherBids() {
        BotView v = BotViews.of(bidding(), "bot-1", new OpenBidMode());

        assertThat(v.visibleBids()).containsExactly(new Bid("p1", 120));
        assertThat(v.leader()).contains(new Bid("p1", 120));
    }

    @Test
    void cardsAlreadyWonOrDiscardedAreNoLongerCandidates() {
        GameState s = bidding()
                .withPlayer("p1", p -> p.withCardInSlot("captain", "c5"))
                .withDiscarded(List.of(Fixtures.card("c6")));
        BotView v = BotViews.of(s, "bot-1", new SealedBidMode());

        assertThat(v.unseen()).extracting(Card::id).hasSize(8).doesNotContain("c5", "c6");
    }
}
