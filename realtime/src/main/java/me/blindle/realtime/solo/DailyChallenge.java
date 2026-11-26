package me.blindle.realtime.solo;

import me.blindle.content.Preset;
import me.blindle.core.GameException;
import me.blindle.modes.ValueVerdictMode;
import me.blindle.realtime.game.GameFactory;
import me.blindle.realtime.room.DailyContext;
import me.blindle.realtime.room.Room;
import me.blindle.realtime.room.RoomRegistry;
import me.blindle.realtime.room.RoomSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Défi du jour (§7) : pour chaque preset {@code daily}, une seed par jour — tout le monde joue
 * les mêmes lots au même prix du marché. Partie solo, score au classement en fin de partie.
 * Le jour change à minuit dans le fuseau {@code blindle.daily.zone} (Europe/Paris par défaut).
 */
@Component
public class DailyChallenge {

    private final RoomRegistry rooms;
    private final GameFactory factory;
    private final DailyLeaderboard leaderboard;
    private final Clock clock;
    private final ZoneId zone;

    public DailyChallenge(RoomRegistry rooms, GameFactory factory, DailyLeaderboard leaderboard, Clock clock,
                          @Value("${blindle.daily.zone:Europe/Paris}") String zone) {
        this.rooms = rooms;
        this.factory = factory;
        this.leaderboard = leaderboard;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    /** Seed du défi : ne dépend que du jour et du preset (FNV-1a 64 bits, stable entre JVM). */
    public static long seed(LocalDate date, String presetId) {
        long h = 0xcbf29ce484222325L;
        for (byte b : (date + "|" + presetId).getBytes(StandardCharsets.UTF_8)) {
            h ^= b & 0xff;
            h *= 0x100000001b3L;
        }
        return h;
    }

    public List<Preset> dailyPresets() {
        return factory.presets().stream().filter(Preset::daily).toList();
    }

    /** Crée la room solo du jour, y fait entrer le joueur et lance la partie. */
    public Room start(String playerId, String name, String presetId) {
        Preset preset = dailyPresets().stream().filter(p -> p.id().equals(presetId)).findFirst()
                .orElseThrow(() -> new GameException("NOT_DAILY", "Ce preset n'est pas un défi du jour"));
        LocalDate date = today();
        Room room = rooms.createDaily(preset.id(), seed(date, preset.id()),
                new DailyContext(date, snapshot -> submit(snapshot, preset.id(), playerId)));
        room.join(playerId, name);
        room.start(playerId);
        return room;
    }

    private int submit(RoomSnapshot snapshot, String presetId, String playerId) {
        var state = snapshot.state();
        var player = state.player(playerId).orElseThrow();
        return leaderboard.submit(snapshot.dailyDate(), presetId,
                new DailyLeaderboard.Entry(player.name(), ValueVerdictMode.score(state, player)));
    }
}
