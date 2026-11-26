package me.blindle.realtime.room;

import me.blindle.core.Phase;
import me.blindle.core.model.Bid;
import me.blindle.core.model.GameState;
import me.blindle.core.model.PlayerState;
import me.blindle.modes.bot.AggressiveBot;
import me.blindle.modes.bot.BlufferBot;
import me.blindle.modes.bot.BotCatalog;
import me.blindle.modes.bot.FanBot;
import me.blindle.modes.bot.StingyBot;
import me.blindle.modes.bot.StrategistBot;
import me.blindle.realtime.RealtimeConfig.RoomSettings;
import me.blindle.realtime.solo.DailyChallenge;
import me.blindle.realtime.solo.DailyLeaderboard;
import me.blindle.realtime.view.ViewProjector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Étape 9 : un humain contre des bots, qui jouent par le même chemin que lui. */
class BotRoomTest {

    private static final BotCatalog BOTS = new BotCatalog(List.of(new AggressiveBot(), new StingyBot(),
            new FanBot(), new BlufferBot(), new StrategistBot()));

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-26T12:00:00Z"), ZoneOffset.UTC);
    private RoomTest.ManualTimers timers;
    private RoomTest.Recorder recorder;

    @BeforeEach
    void setUp() {
        timers = new RoomTest.ManualTimers();
        recorder = new RoomTest.Recorder();
    }

    private Room room(String preset, long seed) {
        return new Room("BOTS", seed, RoomTest.FACTORY.setup(preset), new RoomSettings(3, 20, 60), timers,
                recorder, clock);
    }

    @Test
    void humanAgainstFiveProfilesPlaysAFullSealedGame() {
        Room room = room("solo-manga-bots", 11);
        room.join("me", "Moi");
        BOTS.all().forEach(b -> room.addBot("me", b));
        room.addBot("me", BOTS.require("aggressive"));
        assertThat(room.snapshot().state().players()).extracting(PlayerState::name)
                .contains("Bot Agressif", "Bot Agressif 2", "Bot Radin", "Bot Fan", "Bot Bluffeur", "Bot Stratège");
        room.start("me");

        // Les bots ont déjà joué le premier palier ; leurs offres restent secrètes pour l'humain.
        GameState s = room.snapshot().state();
        assertThat(s.phase()).isEqualTo(Phase.BIDDING);
        assertThat(s.lot().bids()).isNotEmpty().allSatisfy(b -> assertThat(b.playerId()).startsWith("bot-"));
        assertThat(ViewProjector.room(room.snapshot()).lot().bids()).isNull();

        // L'humain passe tout : les bots se partagent les lots.
        playUntilFinished(room);

        s = room.snapshot().state();
        assertThat(s.phase()).isEqualTo(Phase.FINISHED);
        assertThat(s.result().ranking()).hasSize(7);
        int botCards = s.players().stream().filter(PlayerState::bot).mapToInt(p -> p.team().size()).sum();
        assertThat(botCards).isGreaterThan(0);
        assertThat(s.player("me").orElseThrow().team()).isEmpty();
        assertThat(ViewProjector.room(room.snapshot()).players())
                .filteredOn(p -> p.id().startsWith("bot-")).allSatisfy(p -> assertThat(p.bot()).isTrue());
    }

    @Test
    void botsOutbidEachOtherInOpenModeAndTheHumanCanStillWin() {
        Room room = room("manga-chaos", 5);
        room.join("me", "Moi");
        room.addBot("me", BOTS.require("aggressive"));
        room.addBot("me", BOTS.require("bluffer"));
        room.start("me");

        GameState s = room.snapshot().state();
        assertThat(s.phase()).isEqualTo(Phase.BIDDING);
        assertThat(ViewProjector.room(room.snapshot()).lot().bids()).isNotNull();

        // L'humain surenchérit très haut : les bots ne suivent pas au-delà de leur plafond.
        int top = s.lot().bids().stream().mapToInt(Bid::amount).max().orElse(s.lot().floorPrice());
        room.bid("me", Math.min(990, top + 500));
        if (room.snapshot().state().phase() == Phase.BIDDING) {
            timers.fire();
        }
        assertThat(room.snapshot().state().lot().winnerId()).isEqualTo("me");
        playUntilFinished(room);
        assertThat(room.snapshot().state().phase()).isEqualTo(Phase.FINISHED);
    }

    @Test
    void onlyTheHostAddsBotsAndNeverInTheDailyChallenge() {
        Room room = room("solo-manga-bots", 1);
        room.join("me", "Moi");
        room.join("you", "Toi");
        assertThatThrownBy(() -> room.addBot("you", BOTS.require("fan")))
                .hasFieldOrPropertyWithValue("code", "NOT_HOST");
        assertThatThrownBy(() -> BOTS.require("ghost")).hasFieldOrPropertyWithValue("code", "UNKNOWN_BOT");

        DailyChallenge daily = new DailyChallenge(new RoomRegistry(RoomTest.FACTORY, new RoomSettings(3, 20, 60),
                timers, recorder, clock), RoomTest.FACTORY, new DailyLeaderboard(), clock, "Europe/Paris");
        Room solo = daily.start("a", "Alice", "defi-manga");
        assertThatThrownBy(() -> solo.addBot("a", BOTS.require("fan")))
                .hasFieldOrPropertyWithValue("code", "DAILY_IS_SOLO");
    }

    /** Joue jusqu'au bout : l'humain passe ou place sa carte, les minuteries font le reste. */
    private void playUntilFinished(Room room) {
        for (int guard = 0; guard < 500 && room.snapshot().state().phase() != Phase.FINISHED; guard++) {
            GameState s = room.snapshot().state();
            switch (s.phase()) {
                case BIDDING -> {
                    if (s.eligibleBidders().contains("me") && !s.lot().hasActed("me") && !"me".equals(leader(s))) {
                        room.pass("me");
                    } else {
                        timers.fire();
                    }
                }
                case REVEALED -> {
                    if ("me".equals(s.lot().winnerId())) {
                        List<String> slots = Room.placeableSlots(s, room.snapshot().setup().engine(), "me");
                        if (slots.isEmpty()) {
                            room.forfeit("me");
                        } else {
                            room.assignSlot("me", slots.get(0));
                        }
                    } else {
                        // Le bot place sa carte seul, après la courte pause de révélation.
                        assertThat(timers.at).isEqualTo(clock.instant().plusSeconds(Room.BOT_REVEAL_SEC));
                        timers.fire();
                    }
                }
                case VERDICT -> {
                    String other = s.players().stream().filter(PlayerState::bot).findFirst().orElseThrow().id();
                    room.vote("me", other);
                }
                default -> throw new IllegalStateException("Phase inattendue : " + s.phase());
            }
        }
    }

    private static String leader(GameState s) {
        return s.lot().bids().stream().max(Comparator.comparingInt(Bid::amount)).map(Bid::playerId).orElse(null);
    }
}
