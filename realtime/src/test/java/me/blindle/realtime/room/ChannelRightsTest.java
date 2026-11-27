package me.blindle.realtime.room;

import me.blindle.content.Channel;
import me.blindle.content.ContentRegistry;
import me.blindle.modes.bot.BotCatalog;
import me.blindle.realtime.RealtimeConfig.RoomSettings;
import me.blindle.realtime.channel.ChannelAccess;
import me.blindle.realtime.solo.DailyChallenge;
import me.blindle.realtime.solo.DailyLeaderboard;
import me.blindle.realtime.stomp.GameController;
import me.blindle.realtime.stomp.PlayerHandshakeHandler.PlayerPrincipal;
import me.blindle.realtime.stomp.Requests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Étape 10 : canal déduit de l'authentification, filtrage et retrait à chaud (§8). */
class ChannelRightsTest {

    private static final ContentRegistry CONTENT = new ContentRegistry("");

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-26T12:00:00Z"), ZoneOffset.UTC);
    private RoomRegistry rooms;
    private ChannelAccess access;
    private GameController controller;

    private final Principal web = new PlayerPrincipal("w", Channel.WEB);
    private final Principal discord = new PlayerPrincipal("d", Channel.DISCORD);

    @BeforeEach
    void setUp() {
        var timers = new RoomTest.ManualTimers();
        var recorder = new RoomTest.Recorder();
        rooms = new RoomRegistry(RoomTest.FACTORY, new RoomSettings(3, 20, 60), timers, recorder, clock);
        access = new ChannelAccess(CONTENT, List.of());
        DailyChallenge daily = new DailyChallenge(rooms, RoomTest.FACTORY, new DailyLeaderboard(), clock,
                "Europe/Paris");
        controller = new GameController(rooms, daily, new BotCatalog(List.of()), access, CONTENT,
                RoomTest.FACTORY);
    }

    @Test
    void discordPlayerCannotLaunchAWebOnlyTheme() {
        assertThatThrownBy(() -> controller.create(new Requests.CreateRoom("Dina", "manga-classique"), discord))
                .hasFieldOrPropertyWithValue("code", "CHANNEL_FORBIDDEN");
        assertThatThrownBy(() -> controller.startDaily(new Requests.StartDaily("Dina", "defi-manga"), discord))
                .hasFieldOrPropertyWithValue("code", "CHANNEL_FORBIDDEN");
        assertThat(rooms.size()).isZero();
    }

    @Test
    void discordPlayerCanLaunchAThemeOpenToDiscord() {
        controller.create(new Requests.CreateRoom("Dina", "pays-classique"), discord);
        controller.startDaily(new Requests.StartDaily("Dina", "defi-pays"), discord);
        assertThat(rooms.size()).isEqualTo(2);
    }

    @Test
    void discordPlayerCannotJoinAWebRoomByCode() {
        Room room = rooms.create("manga-classique");
        controller.join(new Requests.JoinRoom(room.code(), "Wassim"), web);

        assertThatThrownBy(() -> controller.join(new Requests.JoinRoom(room.code(), "Dina"), discord))
                .hasFieldOrPropertyWithValue("code", "CHANNEL_FORBIDDEN");
        assertThat(room.hasPlayer("d")).isFalse();
    }

    @Test
    void themeCanBeWithdrawnFromAChannelAndRestored() {
        Room before = rooms.create("pays-classique");
        controller.join(new Requests.JoinRoom(before.code(), "Dina"), discord);

        access.block("pays-du-monde", Channel.DISCORD);
        assertThat(access.allows(CONTENT.preset("pays-classique").orElseThrow(), Channel.DISCORD)).isFalse();
        assertThat(access.allows(CONTENT.preset("pays-classique").orElseThrow(), Channel.WEB)).isTrue();
        assertThatThrownBy(() -> controller.create(new Requests.CreateRoom("Dina", "pays-classique"), discord))
                .hasFieldOrPropertyWithValue("code", "CHANNEL_FORBIDDEN");
        assertThatThrownBy(() -> controller.join(new Requests.JoinRoom(before.code(), "Dora"), new PlayerPrincipal("d2", Channel.DISCORD)))
                .hasFieldOrPropertyWithValue("code", "CHANNEL_FORBIDDEN");
        // La partie déjà ouverte continue pour ceux qui y sont.
        assertThat(before.hasPlayer("d")).isTrue();

        access.unblock("pays-du-monde", Channel.DISCORD);
        controller.create(new Requests.CreateRoom("Dina", "pays-classique"), discord);
    }

    @Test
    void withdrawalsCanBeRestoredAtStartup() {
        ChannelAccess fromConfig = new ChannelAccess(CONTENT, List.of("pays-du-monde:discord", " "));
        assertThat(fromConfig.channels(CONTENT.preset("pays-classique").orElseThrow())).containsExactly(Channel.WEB);
        assertThat(fromConfig.status().get("pays-du-monde").blocked()).containsExactly("discord");
        assertThatThrownBy(() -> new ChannelAccess(CONTENT, List.of("inconnu:web")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChannelAccess(CONTENT, List.of("pays-du-monde:fax")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownConnectionIsRefused() {
        Principal stranger = () -> "x";
        assertThatThrownBy(() -> controller.create(new Requests.CreateRoom("X", "pays-classique"), stranger))
                .hasFieldOrPropertyWithValue("code", "NOT_AUTHENTICATED");
    }
}
