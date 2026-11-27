package me.blindle.realtime.room;

import me.blindle.core.GameException;
import me.blindle.realtime.RealtimeConfig.RoomSettings;
import me.blindle.realtime.game.GameFactory;
import me.blindle.realtime.timer.TimerScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rooms en mémoire (MVP), indexées par code. Le code et la seed sont tirés ici :
 * la seed fixe ensuite tout l'aléa de la partie.
 */
@Component
public class RoomRegistry {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RoomRegistry.class);

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // sans I ni O
    private static final int CODE_LENGTH = 4;
    private static final Duration IDLE_TTL = Duration.ofHours(2);

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final GameFactory factory;
    private final RoomSettings settings;
    private final TimerScheduler timers;
    private final Broadcaster broadcaster;
    private final Clock clock;

    public RoomRegistry(GameFactory factory, RoomSettings settings, TimerScheduler timers,
                        Broadcaster broadcaster, Clock clock) {
        this.factory = factory;
        this.settings = settings;
        this.timers = timers;
        this.broadcaster = broadcaster;
        this.clock = clock;
    }

    public Room create(String presetId) {
        return create(presetId, random.nextLong());
    }

    public Room create(String presetId, long seed) {
        return create(presetId, seed, settings, null);
    }

    /** Room solo de défi du jour : un seul joueur, seed du jour. */
    public Room createDaily(String presetId, long seed, DailyContext daily) {
        return create(presetId, seed, settings.solo(), daily);
    }

    private Room create(String presetId, long seed, RoomSettings roomSettings, DailyContext daily) {
        var setup = factory.setup(presetId);
        while (true) {
            String code = newCode();
            Room room = new Room(code, seed, setup, roomSettings, timers, broadcaster, clock, daily);
            if (rooms.putIfAbsent(code, room) == null) {
                return room;
            }
        }
    }

    public Room require(String code) {
        Room room = code == null ? null : rooms.get(code.strip().toUpperCase());
        if (room == null) {
            throw new GameException("ROOM_NOT_FOUND", "Aucune room avec le code " + code);
        }
        return room;
    }

    /** Une connexion est tombée : chaque room où jouait ce joueur en est informée. */
    public void disconnect(String playerId) {
        for (Room room : rooms.values()) {
            if (room.hasPlayer(playerId)) {
                try {
                    room.disconnect(playerId);
                } catch (RuntimeException e) {
                    LOG.warn("Room {} : déconnexion de {} mal traitée", room.code(), playerId, e);
                }
            }
        }
    }

    public int size() {
        return rooms.size();
    }

    /** Fins de délai de reconnexion : passation d'hôte, retrait des absents en salle d'attente. */
    @Scheduled(fixedDelay = 2000)
    public void sweepDisconnected() {
        for (Room room : rooms.values()) {
            try {
                room.sweep();
            } catch (RuntimeException e) {
                LOG.warn("Room {} : délai de reconnexion mal traité", room.code(), e);
            }
        }
    }

    /** Supprime les rooms inactives depuis longtemps. */
    @Scheduled(fixedDelay = 10 * 60 * 1000)
    public void evictIdle() {
        var limit = clock.instant().minus(IDLE_TTL);
        rooms.values().removeIf(r -> r.lastActivity().isBefore(limit));
    }

    private String newCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
