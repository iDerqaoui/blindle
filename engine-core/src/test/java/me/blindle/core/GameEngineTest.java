package me.blindle.core;

import me.blindle.core.event.GameEvent;
import me.blindle.core.event.GameHook;
import me.blindle.core.model.Bid;
import me.blindle.core.model.Card;
import me.blindle.core.model.GameResult;
import me.blindle.core.model.GameState;
import me.blindle.core.model.LotResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameEngineTest {

    private static final Instant DEADLINE = Instant.parse("2026-01-01T00:00:30Z");
    private static final BidResolver NOBODY = (lot, random) -> Optional.empty();

    private final GameEngine engine = new GameEngine();

    /** Résolveur stub : la plus haute offre gagne, au prix offert. */
    private static final BidResolver HIGHEST = (lot, random) -> lot.bids().stream()
            .max((a, b) -> Integer.compare(a.amount(), b.amount()))
            .map(b -> new LotResult(b.playerId(), b.amount()));

    @Test
    void startGivesBudgetsAndShufflesDeck() {
        GameState s = engine.start(Fixtures.lobby(engine, 7, 3)).state();

        assertThat(s.phase()).isEqualTo(Phase.STARTED);
        assertThat(s.players()).allMatch(p -> p.budget() == 1000);
        assertThat(s.deck()).containsExactlyInAnyOrderElementsOf(Fixtures.deck(10));
    }

    @Test
    void sameSeedGivesSameDraws() {
        assertThat(drawnCards(123)).isEqualTo(drawnCards(123));
        assertThat(drawnCards(123)).isNotEqualTo(drawnCards(456));
    }

    private List<String> drawnCards(long seed) {
        GameState s = engine.start(Fixtures.lobby(engine, seed, 3)).state();
        return s.deck().stream().map(Card::id).toList();
    }

    @Test
    void fullGameIsSimulatedFromLobbyToFinished() {
        List<GameHook> hooks = new ArrayList<>();
        GameEngine recording = new GameEngine((event, state) -> {
            hooks.add(event.hook());
            return state;
        });
        GameState s = recording.start(Fixtures.lobby(recording, 1, 3)).state();
        String[] winners = {"p1", "p2", "p3"};

        for (String winner : winners) {
            s = recording.nextRound(s).state();
            assertThat(s.phase()).isEqualTo(Phase.DRAW);
            s = recording.revealFirstHint(s).state();
            s = recording.openBidding(s, DEADLINE).state();
            s = recording.placeBid(s, new Bid(winner, 200)).state();
            s = recording.closeBidding(s, HIGHEST).state();
            assertThat(s.phase()).isEqualTo(Phase.AWARDED);
            s = recording.revealCard(s).state();
            String cardId = s.lot().cards().get(0).id();
            s = recording.assignSlot(s, winner, "captain", cardId).state();
            assertThat(s.phase()).isEqualTo(Phase.SLOT);
        }
        s = recording.nextRound(s).state();
        assertThat(s.phase()).isEqualTo(Phase.VERDICT);

        s = recording.finish(s, new GameResult(List.of("p1", "p2", "p3"), Map.of())).state();

        assertThat(s.phase()).isEqualTo(Phase.FINISHED);
        assertThat(s.players()).allMatch(p -> p.budget() == 800 && p.team().size() == 1);
        assertThat(s.roundIndex()).isEqualTo(3);
        assertThat(hooks).startsWith(GameHook.ON_GAME_START, GameHook.ON_ROUND_START,
                GameHook.ON_HINT_REVEAL, GameHook.ON_BID_PLACED, GameHook.ON_LOT_WON,
                GameHook.ON_CARD_REVEALED, GameHook.ON_SLOT_ASSIGNED, GameHook.ON_ROUND_END);
        assertThat(hooks).endsWith(GameHook.ON_ROUND_END, GameHook.ON_VERDICT);
    }

    @Test
    void noWinnerRevealsNextHintThenDiscardsTheLot() {
        GameState s = engine.start(Fixtures.lobby(engine, 1, 2)).state();
        s = engine.nextRound(s).state();
        s = engine.revealFirstHint(s).state();
        Card lotCard = s.lot().cards().get(0);

        for (int level = 2; level <= 3; level++) {
            s = engine.openBidding(s, DEADLINE).state();
            s = engine.closeBidding(s, NOBODY).state();
            assertThat(s.phase()).isEqualTo(Phase.HINT);
            assertThat(s.lot().hintLevel()).isEqualTo(level);
        }
        s = engine.openBidding(s, DEADLINE).state();
        var step = engine.closeBidding(s, NOBODY);

        assertThat(step.state().phase()).isEqualTo(Phase.DRAW);
        assertThat(step.state().roundIndex()).isEqualTo(2);
        assertThat(step.state().discarded()).containsExactly(lotCard);
        assertThat(step.events()).containsSequence(new GameEvent.LotDiscarded(1, null), new GameEvent.RoundEnded(1, false));
    }

    @Test
    void invalidActionsAreRefusedWithoutChangingState() {
        GameState lobby = Fixtures.lobby(engine, 1, 3);
        assertThatThrownBy(() -> engine.nextRound(lobby))
                .isInstanceOf(GameException.class);
        assertThatThrownBy(() -> engine.openBidding(lobby, DEADLINE))
                .isInstanceOf(IllegalPhaseTransitionException.class);

        GameState started = engine.start(lobby).state();
        assertThatThrownBy(() -> engine.addPlayer(started, "p4", "Dan", false))
                .isInstanceOf(GameException.class);
        assertThatThrownBy(() -> engine.start(started))
                .isInstanceOf(IllegalPhaseTransitionException.class);
        assertThat(lobby.phase()).isEqualTo(Phase.LOBBY);
    }

    @Test
    void bidsAreValidated() {
        GameState s = engine.start(Fixtures.lobby(engine, 1, 3)).state();
        s = engine.openBidding(engine.revealFirstHint(engine.nextRound(s).state()).state(), DEADLINE)
                .state();
        GameState bidding = s.withLot(s.lot().withFloorPrice(100));

        assertThatThrownBy(() -> engine.placeBid(bidding, new Bid("p1", 50)))
                .hasMessageContaining("100");
        assertThatThrownBy(() -> engine.placeBid(bidding, new Bid("ghost", 150)))
                .isInstanceOf(GameException.class);
        GameState twice = engine.placeBid(engine.placeBid(bidding, new Bid("p1", 150)).state(),
                new Bid("p1", 300)).state();
        assertThat(twice.lot().bids()).containsExactly(new Bid("p1", 300));
    }

    @Test
    void onlyWinnerPlacesCardInFreeSlot() {
        GameState s = engine.start(Fixtures.lobby(engine, 1, 3)).state();
        s = engine.openBidding(engine.revealFirstHint(engine.nextRound(s).state()).state(), DEADLINE)
                .state();
        s = engine.placeBid(s, new Bid("p2", 120)).state();
        s = engine.revealCard(engine.closeBidding(s, HIGHEST).state()).state();
        GameState revealed = s;
        String cardId = s.lot().cards().get(0).id();

        assertThatThrownBy(() -> engine.assignSlot(revealed, "p1", "captain", cardId))
                .hasFieldOrPropertyWithValue("code", "NOT_WINNER");
        assertThatThrownBy(() -> engine.assignSlot(revealed, "p2", "nowhere", cardId))
                .hasFieldOrPropertyWithValue("code", "UNKNOWN_SLOT");
        assertThat(engine.assignSlot(revealed, "p2", "support", cardId).state().player("p2")
                .orElseThrow().team()).containsEntry("support", cardId);
    }

    @Test
    void dispatcherRejectionAbortsTheAction() {
        GameEngine strict = new GameEngine((event, state) -> {
            if (event instanceof GameEvent.BidPlaced) {
                throw new GameException("REJECTED", "Refusé par une règle");
            }
            return state;
        });
        GameState s = strict.start(Fixtures.lobby(strict, 1, 3)).state();
        GameState bidding = strict.openBidding(
                strict.revealFirstHint(strict.nextRound(s).state()).state(), DEADLINE).state();

        assertThatThrownBy(() -> strict.placeBid(bidding, new Bid("p1", 100)))
                .hasFieldOrPropertyWithValue("code", "REJECTED");
        assertThat(bidding.lot().bids()).isEmpty();
    }

    @Test
    void versionIncreasesOnEveryAction() {
        GameState lobby = Fixtures.lobby(engine, 1, 3);
        GameState started = engine.start(lobby).state();
        assertThat(started.version()).isEqualTo(lobby.version() + 1);
    }
}
