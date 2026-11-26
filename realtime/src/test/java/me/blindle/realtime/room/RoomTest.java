package me.blindle.realtime.room;

import me.blindle.content.ContentRegistry;
import me.blindle.core.GameException;
import me.blindle.core.Phase;
import me.blindle.modes.ModeCatalog;
import me.blindle.modes.OpenBidMode;
import me.blindle.modes.SealedBidMode;
import me.blindle.modes.VoteVerdictMode;
import me.blindle.realtime.RealtimeConfig.RoomSettings;
import me.blindle.realtime.game.GameFactory;
import me.blindle.realtime.timer.TimerScheduler;
import me.blindle.realtime.view.ViewProjector;
import me.blindle.realtime.view.Views;
import me.blindle.rules.AntiSnipeRule;
import me.blindle.rules.BudgetCapRule;
import me.blindle.rules.JokerSpyRule;
import me.blindle.rules.JokerStealRule;
import me.blindle.rules.SlotConstraintRule;
import me.blindle.rules.TagSynergyRule;
import me.blindle.rules.TrapLotsRule;
import me.blindle.rules.RisingFloorRule;
import me.blindle.rules.RuleCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomTest {

    static final GameFactory FACTORY = new GameFactory(new ContentRegistry(""),
            new RuleCatalog(List.of(new BudgetCapRule(), new RisingFloorRule(), new JokerSpyRule(),
                    new JokerStealRule(), new TrapLotsRule(), new TagSynergyRule(), new SlotConstraintRule(),
                    new AntiSnipeRule())),
            new ModeCatalog(List.of(new SealedBidMode(), new OpenBidMode()), List.of(new VoteVerdictMode())));

    /** Minuteries manuelles : on déclenche à la main la dernière programmée. */
    static final class ManualTimers implements TimerScheduler {
        Runnable pending;
        Instant at;

        @Override
        public Handle schedule(Instant at, Runnable task) {
            this.pending = task;
            this.at = at;
            return () -> {
                if (pending == task) {
                    pending = null;
                }
            };
        }

        void fire() {
            Runnable task = pending;
            pending = null;
            task.run();
        }
    }

    static final class Recorder implements Broadcaster {
        final List<RoomSnapshot> published = new ArrayList<>();

        @Override
        public void publish(RoomSnapshot snapshot) {
            published.add(snapshot);
        }

        @Override
        public void sendTo(RoomSnapshot snapshot, String playerId) {
            published.add(snapshot);
        }

        RoomSnapshot last() {
            return published.get(published.size() - 1);
        }
    }

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-26T12:00:00Z"), ZoneOffset.UTC);
    private ManualTimers timers;
    private Recorder recorder;
    private Room room;

    @BeforeEach
    void setUp() {
        timers = new ManualTimers();
        recorder = new Recorder();
        room = new Room("ABCD", 42, FACTORY.setup("manga-classique"), new RoomSettings(3, 20, 60),
                timers, recorder, clock);
        room.join("a", "Alice");
        room.join("b", "Bilal");
        room.join("c", "Chloé");
    }

    @Test
    void onlyHostStartsWithEnoughPlayers() {
        assertThatThrownBy(() -> room.start("b")).hasFieldOrPropertyWithValue("code", "NOT_HOST");

        room.start("a");

        var s = room.snapshot().state();
        assertThat(s.phase()).isEqualTo(Phase.BIDDING);
        assertThat(s.lot().hintLevel()).isEqualTo(1);
        assertThat(s.lot().floorPrice()).isEqualTo(50);
        assertThat(s.lot().deadline()).isEqualTo(clock.instant().plusSeconds(30));
        assertThat(timers.at).isEqualTo(s.lot().deadline());
    }

    @Test
    void notEnoughPlayers() {
        Room small = new Room("WXYZ", 1, FACTORY.setup("manga-classique"), new RoomSettings(3, 20, 60),
                timers, recorder, clock);
        small.join("a", "Alice");
        assertThatThrownBy(() -> small.start("a")).hasFieldOrPropertyWithValue("code", "NOT_ENOUGH_PLAYERS");
    }

    @Test
    void tierClosesAsSoonAsEveryoneActed() {
        room.start("a");
        room.bid("a", 80);
        room.pass("b");
        assertThat(room.snapshot().state().phase()).isEqualTo(Phase.BIDDING);

        room.pass("c");

        var s = room.snapshot().state();
        assertThat(s.phase()).isEqualTo(Phase.REVEALED);
        assertThat(s.lot().winnerId()).isEqualTo("a");
        assertThat(s.player("a").orElseThrow().budget()).isEqualTo(920);
    }

    @Test
    void bidTimeoutRevealsNextHintWithHigherFloor() {
        room.start("a");
        timers.fire();

        var s = room.snapshot().state();
        assertThat(s.phase()).isEqualTo(Phase.BIDDING);
        assertThat(s.lot().hintLevel()).isEqualTo(2);
        assertThat(s.lot().floorPrice()).isEqualTo(100);
    }

    @Test
    void slotTimeoutPlacesCardAutomaticallyAndNextRoundStarts() {
        room.start("a");
        room.bid("b", 60);
        room.pass("a");
        room.pass("c");
        assertThat(room.snapshot().state().phase()).isEqualTo(Phase.REVEALED);

        timers.fire();

        var s = room.snapshot().state();
        assertThat(s.player("b").orElseThrow().team()).hasSize(1);
        assertThat(s.phase()).isEqualTo(Phase.BIDDING);
        assertThat(s.roundIndex()).isEqualTo(2);
    }

    @Test
    void winnerChoosesSlot() {
        room.start("a");
        room.bid("c", 60);
        room.pass("a");
        room.pass("b");
        assertThatThrownBy(() -> room.assignSlot("a", "captain")).isInstanceOf(GameException.class);

        room.assignSlot("c", "joker");

        assertThat(room.snapshot().state().player("c").orElseThrow().team()).containsKey("joker");
    }

    @Test
    void fullGameThroughVotes() {
        room.start("a");
        String[] ids = {"a", "b", "c"};
        for (int round = 0; round < 10; round++) {
            String winner = ids[round % 3];
            room.bid(winner, 50);
            for (String id : ids) {
                if (!id.equals(winner)) {
                    room.pass(id);
                }
            }
            room.assignSlot(winner, room.snapshot().state().player(winner).orElseThrow().team().isEmpty()
                    ? "captain" : ViewProjector.player(room.snapshot(), winner).freeSlots().get(0));
        }
        assertThat(room.snapshot().state().phase()).isEqualTo(Phase.VERDICT);
        assertThatThrownBy(() -> room.vote("a", "a")).hasFieldOrPropertyWithValue("code", "SELF_VOTE");

        room.vote("a", "b");
        room.vote("b", "c");
        room.vote("c", "b");

        var s = room.snapshot().state();
        assertThat(s.phase()).isEqualTo(Phase.FINISHED);
        assertThat(s.result().ranking().get(0)).isEqualTo("b");
    }

    @Test
    void voteTimeoutFinishesWithReceivedVotes() {
        room.start("a");
        for (int round = 0; round < 10; round++) {
            timers.fire(); // palier 1 sans offre
            timers.fire(); // palier 2
            timers.fire(); // palier 3 → invendu, round suivant
        }
        assertThat(room.snapshot().state().phase()).isEqualTo(Phase.VERDICT);
        room.vote("a", "c");

        timers.fire();

        assertThat(room.snapshot().state().phase()).isEqualTo(Phase.FINISHED);
        assertThat(room.snapshot().state().result().scores()).containsEntry("c", 1);
    }

    @Test
    void staleTimerIsIgnored() {
        room.start("a");
        Runnable firstTier = timers.pending;
        room.bid("a", 80);
        room.pass("b");
        room.pass("c"); // palier clos, carte révélée, nouveau timer de placement

        firstTier.run(); // l'ancien timer ne doit rien faire

        assertThat(room.snapshot().state().phase()).isEqualTo(Phase.REVEALED);
    }

    @Test
    void sealedViewsNeverLeakBidsOrCardBeforeResolution() {
        room.start("a");
        room.bid("a", 120);
        room.bid("b", 90);

        Views.RoomView view = ViewProjector.room(recorder.last());
        assertThat(view.lot().bids()).isNull();
        assertThat(view.lot().cards()).isNull();
        assertThat(view.lot().hints()).hasSize(1);
        assertThat(view.lot().actedCount()).isEqualTo(2);
        String cardName = recorder.last().state().lot().cards().get(0).name();
        assertThat(view.toString()).doesNotContain(cardName);
        assertThat(view.players()).filteredOn(Views.PlayerSummary::acted).hasSize(2);

        Views.PlayerView mine = ViewProjector.player(recorder.last(), "b");
        assertThat(mine.myBid()).isEqualTo(90);
        assertThat(mine.toString()).doesNotContain("120");

        room.pass("c");
        Views.RoomView resolved = ViewProjector.room(recorder.last());
        assertThat(resolved.lot().bids()).extracting(Views.BidView::amount).containsExactlyInAnyOrder(120, 90);
        assertThat(resolved.lot().cards()).extracting(Views.CardView::name).containsExactly(cardName);
        assertThat(resolved.lot().winnerId()).isEqualTo("a");
    }

    @Test
    void settingsOnlyInLobby() {
        room.updateSettings("a", 1500, 5, 20);
        assertThat(room.snapshot().state().settings().budget()).isEqualTo(1500);
        assertThatThrownBy(() -> room.updateSettings("a", null, 99, null))
                .hasFieldOrPropertyWithValue("code", "TOO_MANY_ROUNDS");
        room.start("a");
        assertThatThrownBy(() -> room.updateSettings("a", 10, null, null)).isInstanceOf(GameException.class);
    }

    @Test
    void badNamesAreRefused() {
        Room r = new Room("QQQQ", 1, FACTORY.setup("manga-classique"), new RoomSettings(3, 20, 60),
                timers, recorder, clock);
        assertThatThrownBy(() -> r.join("x", "   ")).hasFieldOrPropertyWithValue("code", "BAD_NAME");
        assertThatThrownBy(() -> r.join("x", "x".repeat(21))).hasFieldOrPropertyWithValue("code", "BAD_NAME");
    }
}
