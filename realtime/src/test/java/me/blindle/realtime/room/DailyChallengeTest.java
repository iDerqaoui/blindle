package me.blindle.realtime.room;

import me.blindle.core.Phase;
import me.blindle.core.model.GameState;
import me.blindle.modes.ValueVerdictMode;
import me.blindle.realtime.RealtimeConfig.RoomSettings;
import me.blindle.realtime.solo.DailyChallenge;
import me.blindle.realtime.solo.DailyLeaderboard;
import me.blindle.realtime.view.ViewProjector;
import me.blindle.realtime.view.Views;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Étape 9 : défi du jour en solo contre le marché, verdict value, classement, partage. */
class DailyChallengeTest {

    // 23 h 30 UTC = 0 h 30 à Paris : le défi du jour est déjà celui du 27.
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-26T23:30:00Z"), ZoneOffset.UTC);
    private RoomTest.ManualTimers timers;
    private RoomTest.Recorder recorder;
    private DailyLeaderboard leaderboard;
    private DailyChallenge daily;

    @BeforeEach
    void setUp() {
        timers = new RoomTest.ManualTimers();
        recorder = new RoomTest.Recorder();
        leaderboard = new DailyLeaderboard();
        RoomRegistry registry = new RoomRegistry(RoomTest.FACTORY, new RoomSettings(3, 20, 60), timers, recorder,
                clock);
        daily = new DailyChallenge(registry, RoomTest.FACTORY, leaderboard, clock, "Europe/Paris");
    }

    @Test
    void dayFollowsTheConfiguredZone() {
        assertThat(daily.today()).isEqualTo(LocalDate.of(2026, 11, 27));
    }

    @Test
    void seedDependsOnlyOnDayAndPreset() {
        LocalDate day = LocalDate.of(2026, 11, 27);
        assertThat(DailyChallenge.seed(day, "defi-pays")).isEqualTo(DailyChallenge.seed(day, "defi-pays"));
        assertThat(DailyChallenge.seed(day, "defi-pays")).isNotEqualTo(DailyChallenge.seed(day.plusDays(1), "defi-pays"));
        assertThat(DailyChallenge.seed(day, "defi-pays")).isNotEqualTo(DailyChallenge.seed(day, "defi-manga"));
    }

    @Test
    void everyoneDrawsTheSameLotsOnTheSameDay() {
        GameState a = daily.start("a", "Alice", "defi-pays").snapshot().state();
        GameState b = daily.start("b", "Bilal", "defi-pays").snapshot().state();

        assertThat(a.seed()).isEqualTo(b.seed());
        assertThat(a.lot().cards()).isEqualTo(b.lot().cards());
        assertThat(a.deck()).isEqualTo(b.deck());
    }

    @Test
    void soloRoomStartsAtOnceAndRefusesASecondPlayer() {
        Room room = daily.start("a", "Alice", "defi-manga");

        assertThat(room.snapshot().state().phase()).isEqualTo(Phase.BIDDING);
        assertThat(room.snapshot().setup().bidMode().id()).isEqualTo("market");
        assertThatThrownBy(() -> room.join("b", "Bilal")).hasFieldOrPropertyWithValue("code", "ROOM_FULL");
    }

    @Test
    void onlyDailyPresetsCanBeStarted() {
        assertThatThrownBy(() -> daily.start("a", "Alice", "pays-classique"))
                .hasFieldOrPropertyWithValue("code", "NOT_DAILY");
    }

    @Test
    void fullGameIsScoredRankedAndShareable() {
        Room first = daily.start("a", "Alice", "defi-pays");
        play(first, "a", 1);
        Room second = daily.start("b", "Bilal", "defi-pays");
        play(second, "b", 2);

        RoomSnapshot snap = first.snapshot();
        GameState s = snap.state();
        assertThat(s.phase()).isEqualTo(Phase.FINISHED);
        int score = ValueVerdictMode.score(s, s.player("a").orElseThrow());
        assertThat(s.result().scores()).containsEntry("a", score);

        // Une ligne par round joué, une case par palier (4 pour ce thème), aucun nom de carte.
        assertThat(snap.history()).hasSize(s.settings().rounds());
        Views.DailyView view = ViewProjector.room(snap).daily();
        assertThat(view.date()).isEqualTo("2026-11-27");
        assertThat(view.rank()).isNotNull();
        List<String> lines = view.share().lines().toList();
        assertThat(lines.get(0)).isEqualTo("Blindle · Défi du jour 27/11/2026");
        assertThat(lines.get(1)).isEqualTo("Pays du monde · score " + (score > 0 ? "+" : "") + score);
        assertThat(lines.subList(2, lines.size() - 1)).hasSize(s.settings().rounds())
                .allSatisfy(row -> assertThat(row.codePoints().count()).isEqualTo(4)
                        .as("ligne %s", row));
        assertThat(lines.get(lines.size() - 1)).isEqualTo("blindle.app");
        s.cards().values().forEach(c -> assertThat(view.share()).doesNotContain(c.name()));

        // Classement du jour : deux parties, meilleur score d'abord.
        List<DailyLeaderboard.Entry> top = leaderboard.top(LocalDate.of(2026, 11, 27), "defi-pays", 10);
        assertThat(top).hasSize(2);
        assertThat(top.get(0).score()).isGreaterThanOrEqualTo(top.get(1).score());
        assertThat(top).extracting(DailyLeaderboard.Entry::name).containsExactlyInAnyOrder("Alice", "Bilal");
    }

    /** Joue une partie complète : mise d'un même montant à chaque palier selon le style. */
    private void play(Room room, String playerId, int style) {
        for (int guard = 0; guard < 200 && room.snapshot().state().phase() != Phase.FINISHED; guard++) {
            GameState s = room.snapshot().state();
            switch (s.phase()) {
                case BIDDING -> {
                    int budget = s.player(playerId).orElseThrow().budget();
                    int wanted = style == 1 ? 90 : 160;
                    if (s.eligibleBidders().contains(playerId) && budget >= Math.max(wanted, s.lot().floorPrice())) {
                        room.bid(playerId, Math.max(wanted, s.lot().floorPrice()));
                    } else {
                        timers.fire();
                    }
                }
                case REVEALED -> {
                    List<String> slots = Room.placeableSlots(s, room.snapshot().setup().engine(), playerId);
                    if (slots.isEmpty()) {
                        room.forfeit(playerId);
                    } else {
                        room.assignSlot(playerId, slots.get(0));
                    }
                }
                default -> throw new IllegalStateException("Phase inattendue : " + s.phase());
            }
        }
    }
}
