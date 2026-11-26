package me.blindle.realtime.room;

import me.blindle.content.ContentRegistry;
import me.blindle.content.ContentValidationException;
import me.blindle.core.Phase;
import me.blindle.core.model.Card;
import me.blindle.modes.ModeCatalog;
import me.blindle.modes.OpenBidMode;
import me.blindle.modes.SealedBidMode;
import me.blindle.modes.VoteVerdictMode;
import me.blindle.realtime.RealtimeConfig.RoomSettings;
import me.blindle.realtime.game.GameFactory;
import me.blindle.realtime.view.ViewProjector;
import me.blindle.realtime.view.Views;
import me.blindle.rules.AntiSnipeRule;
import me.blindle.rules.BudgetCapRule;
import me.blindle.rules.RuleCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Preset manga-chaos : mode open, jokers, pièges, contrainte de slot, anti-snipe. */
class ChaosRoomTest {

    /** Horloge réglable à la main. */
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-26T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private RoomTest.ManualTimers timers;
    private RoomTest.Recorder recorder;
    private Room room;

    @BeforeEach
    void setUp() {
        timers = new RoomTest.ManualTimers();
        recorder = new RoomTest.Recorder();
        room = new Room("CHAO", 42, RoomTest.FACTORY.setup("manga-chaos"), new RoomSettings(3, 20, 60),
                timers, recorder, clock);
        room.join("a", "Alice");
        room.join("b", "Bilal");
        room.join("c", "Chloé");
        room.start("a");
    }

    @Test
    void trapsOnlyInPresetsWithTrapLots() {
        assertThat(RoomTest.FACTORY.setup("manga-chaos").deck()).anyMatch(Card::trap).hasSize(33);
        assertThat(RoomTest.FACTORY.setup("manga-classique").deck()).noneMatch(Card::trap).hasSize(30);
    }

    @Test
    void openBidsAreVisibleAndMustRise() {
        room.bid("a", 60);
        Views.RoomView view = ViewProjector.room(recorder.last());
        assertThat(view.lot().bids()).extracting(Views.BidView::amount).containsExactly(60);

        assertThatThrownBy(() -> room.bid("b", 60)).hasFieldOrPropertyWithValue("code", "BID_TOO_LOW");
        assertThatThrownBy(() -> room.pass("a")).hasFieldOrPropertyWithValue("code", "LEADER_CANNOT_PASS");

        room.bid("b", 70);
        room.pass("a");
        room.pass("c");
        var s = room.snapshot().state();
        assertThat(s.phase()).isIn(Phase.REVEALED, Phase.SLOT, Phase.BIDDING);
        assertThat(s.player("b").orElseThrow().budget()).isLessThanOrEqualTo(930);
    }

    @Test
    void lastSecondBidPushesServerTimer() {
        Instant deadline = room.snapshot().state().lot().deadline();
        clock.now = deadline.minusSeconds(1);

        room.bid("a", 60);

        Instant extended = room.snapshot().state().lot().deadline();
        assertThat(extended).isEqualTo(deadline.plusSeconds(4));
        assertThat(timers.at).isEqualTo(extended);
    }

    @Test
    void spyHintIsOnlyInOwnersPrivateView() {
        room.useJoker("a", "spy", null);

        RoomSnapshot snap = recorder.last();
        Views.PlayerView alice = ViewProjector.player(snap, "a");
        Views.PlayerView bilal = ViewProjector.player(snap, "b");
        assertThat(alice.privateHints()).hasSize(1).first().extracting(Views.HintView::level).isEqualTo(2);
        assertThat(alice.jokers()).containsEntry("spy", 0).containsEntry("steal", 1);
        assertThat(bilal.privateHints()).isEmpty();
        assertThat(ViewProjector.room(snap).lot().hints()).hasSize(1);
        assertThat(ViewProjector.room(snap).toString()).doesNotContain(alice.privateHints().get(0).value());
    }

    @Test
    void stealThroughRoom() {
        room.bid("a", 60);
        room.pass("b");
        room.pass("c");
        if (room.snapshot().state().phase() == Phase.REVEALED) {
            List<String> slots = ViewProjector.player(recorder.last(), "a").placeableSlots();
            room.assignSlot("a", slots.get(0));
        }
        var s = room.snapshot().state();
        String slot = s.player("a").orElseThrow().team().keySet().iterator().next();
        assertThat(s.phase()).isEqualTo(Phase.BIDDING);

        room.useJoker("b", "steal", "a/" + slot);

        var after = room.snapshot().state();
        assertThat(after.player("a").orElseThrow().team()).isEmpty();
        assertThat(after.player("b").orElseThrow().team()).hasSize(1);
    }

    @Test
    void forfeitOnlyWhenNoSlotAccepts() {
        room.bid("a", 60);
        room.pass("b");
        room.pass("c");
        if (room.snapshot().state().phase() == Phase.REVEALED) {
            assertThatThrownBy(() -> room.forfeit("a")).hasFieldOrPropertyWithValue("code", "CAN_PLACE");
        }
    }

    @Test
    void slotTimeoutNeverBreaksConstraint() {
        for (int round = 0; round < 10 && room.snapshot().state().phase() != Phase.VERDICT; round++) {
            var s = room.snapshot().state();
            if (s.phase() == Phase.BIDDING) {
                String bidder = List.of("a", "b", "c").get(round % 3);
                if (s.eligibleBidders().contains(bidder)) {
                    room.bid(bidder, s.lot().floorPrice() + 1);
                }
                timers.fire(); // fin du palier
            }
            if (room.snapshot().state().phase() == Phase.REVEALED) {
                timers.fire(); // placement automatique
            }
        }
        var s = room.snapshot().state();
        for (var p : s.players()) {
            String supportCard = p.team().get("support");
            if (supportCard != null) {
                assertThat(s.card(supportCard).orElseThrow().tags()).contains("support");
            }
        }
    }

    @Test
    void incompatiblePresetsAreRefusedAtStartup(@TempDir Path dir) throws IOException {
        Path base = ContentRegistry.resolveDir("");
        Files.createDirectories(dir.resolve("themes"));
        Files.createDirectories(dir.resolve("presets"));
        Files.copy(base.resolve("themes/manga-shonen.json"), dir.resolve("themes/manga-shonen.json"));
        Files.writeString(dir.resolve("presets/bad.json"), """
                { "id": "bad", "name": "Bad", "theme": "manga-shonen", "bidMode": "sealed", "verdictMode": "vote",
                  "settings": { "budget": 1000, "rounds": 5, "teamSize": 5, "hintLevels": 3, "bidTimerSec": 30 },
                  "rules": [ { "ref": "budget-cap" }, { "ref": "anti-snipe", "params": { "extendSeconds": 5 } } ] }
                """);

        assertThatThrownBy(() -> new GameFactory(new ContentRegistry(dir.toString()),
                new RuleCatalog(List.of(new BudgetCapRule(), new AntiSnipeRule())),
                new ModeCatalog(List.of(new SealedBidMode(), new OpenBidMode()), List.of(new VoteVerdictMode()))))
                .isInstanceOf(ContentValidationException.class)
                .hasMessageContaining("anti-snipe")
                .hasMessageContaining("open");
    }
}
