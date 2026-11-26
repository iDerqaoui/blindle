package me.blindle.realtime.room;

import me.blindle.core.Phase;
import me.blindle.realtime.RealtimeConfig.RoomSettings;
import me.blindle.realtime.view.ViewProjector;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Étape 8 : le thème pays-du-monde se joue sans aucune modification du moteur. */
class CountriesRoomTest {

    @Test
    void fullGameWithFourHintLevelsAndIslandSlot() {
        var timers = new RoomTest.ManualTimers();
        var recorder = new RoomTest.Recorder();
        Room room = new Room("PAYS", 7, RoomTest.FACTORY.setup("pays-classique"), new RoomSettings(3, 20, 60),
                timers, recorder, Clock.fixed(Instant.parse("2026-11-26T12:00:00Z"), ZoneOffset.UTC));
        List<String> ids = List.of("a", "b", "c");
        ids.forEach(id -> room.join(id, id.toUpperCase()));
        room.start("a");

        // Round 1 : personne ne mise, on va jusqu'au 4e indice (plancher 40 + 3×40).
        for (int i = 0; i < 3; i++) {
            timers.fire();
        }
        var s = room.snapshot().state();
        assertThat(s.lot().hintLevel()).isEqualTo(4);
        assertThat(s.lot().floorPrice()).isEqualTo(160);
        assertThat(ViewProjector.room(room.snapshot()).lot().hints()).hasSize(4);

        int round = 0;
        while (room.snapshot().state().phase() != Phase.VERDICT && round < 50) {
            s = room.snapshot().state();
            if (s.phase() == Phase.BIDDING) {
                String bidder = ids.get(round++ % 3);
                if (s.eligibleBidders().contains(bidder)) {
                    room.bid(bidder, s.lot().floorPrice());
                    ids.stream().filter(id -> !id.equals(bidder) && room.snapshot().state().phase() == Phase.BIDDING)
                            .forEach(room::pass);
                } else {
                    timers.fire();
                }
            }
            s = room.snapshot().state();
            if (s.phase() == Phase.REVEALED) {
                String winner = s.lot().winnerId();
                List<String> slots = Room.placeableSlots(s, RoomTest.FACTORY.setup("pays-classique").engine(), winner);
                if (slots.isEmpty()) {
                    room.forfeit(winner);
                } else {
                    room.assignSlot(winner, slots.get(0));
                }
            }
        }
        assertThat(room.snapshot().state().phase()).isEqualTo(Phase.VERDICT);
        room.vote("a", "b");
        room.vote("b", "c");
        room.vote("c", "a");

        s = room.snapshot().state();
        assertThat(s.phase()).isEqualTo(Phase.FINISHED);
        for (var p : s.players()) {
            String island = p.team().get("ile");
            if (island != null) {
                assertThat(s.card(island).orElseThrow().tags()).contains("ile");
            }
        }
    }
}
