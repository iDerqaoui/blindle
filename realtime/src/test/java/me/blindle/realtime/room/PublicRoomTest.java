package me.blindle.realtime.room;

import me.blindle.realtime.RealtimeConfig.RoomSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Rooms publiques : privées par défaut, listées seulement en attente de joueurs, réglées par l'hôte. */
class PublicRoomTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-28T12:00:00Z"), ZoneOffset.UTC);
    private RoomTest.Recorder recorder;
    private Room room;

    @BeforeEach
    void setUp() {
        recorder = new RoomTest.Recorder();
        room = new Room("ABCD", 42, RoomTest.FACTORY.setup("manga-classique"), new RoomSettings(2, 3, 20, 60),
                new RoomTest.ManualTimers(), recorder, clock);
        room.join("a", "Alice");
    }

    @Test
    void roomIsPrivateByDefault() {
        assertThat(room.listing(3)).isEmpty();
        assertThat(recorder.last().listed()).isFalse();
    }

    @Test
    void onlyTheHostMakesItPublicAndTheListingShowsItWhileItWaits() {
        room.join("b", "Bilal");
        assertThatThrownBy(() -> room.setListed("b", true)).hasFieldOrPropertyWithValue("code", "NOT_HOST");

        room.setListed("a", true);
        assertThat(recorder.last().listed()).isTrue();
        Room.PublicRoom listed = room.listing(3).orElseThrow();
        assertThat(listed.code()).isEqualTo("ABCD");
        assertThat(listed.hostName()).isEqualTo("Alice");
        assertThat(listed.humans()).isEqualTo(2);
        assertThat(listed.presetId()).isEqualTo("manga-classique");

        room.setListed("a", false);
        assertThat(room.listing(3)).isEmpty();
    }

    @Test
    void fullOrStartedRoomsLeaveTheList() {
        room.setListed("a", true);
        room.join("b", "Bilal");
        room.join("c", "Chloé");
        assertThat(room.listing(3)).as("complète").isEmpty();

        room.start("a");
        assertThat(room.listing(10)).as("partie lancée").isEmpty();
    }

    @Test
    void absentHostHidesTheRoom() {
        room.setListed("a", true);
        room.disconnect("a");
        assertThat(room.listing(3)).isEmpty();
    }
}
