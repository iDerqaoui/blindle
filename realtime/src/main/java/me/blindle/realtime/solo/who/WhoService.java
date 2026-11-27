package me.blindle.realtime.solo.who;

import me.blindle.core.GameException;
import me.blindle.realtime.solo.DailyLeaderboard;
import me.blindle.realtime.solo.who.WhoRun.Outcome;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Défi « Who's That? » : un essai par jour et par joueur ({@code key} : compte connecté ou cookie du
 * navigateur), en mémoire. Chaque appel relit l'heure serveur ; une carte expirée part d'elle-même.
 */
@Component
public class WhoService {

    public static final String LEADERBOARD_ID = "who";
    static final int MAX_NAME = 20;

    private final WhoDaily daily;
    private final DailyLeaderboard leaderboard;
    private final Blurrer blurrer;
    private final Clock clock;
    private final ZoneId zone;
    private final Map<String, WhoRun> runs = new ConcurrentHashMap<>();

    public WhoService(WhoDaily daily, DailyLeaderboard leaderboard, Blurrer blurrer, Clock clock,
                      @org.springframework.beans.factory.annotation.Value("${blindle.daily.zone:Europe/Paris}") String zone) {
        this.daily = daily;
        this.leaderboard = leaderboard;
        this.blurrer = blurrer;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    public Instant now() {
        return clock.instant();
    }

    public WhoDaily daily() {
        return daily;
    }

    public DailyLeaderboard leaderboard() {
        return leaderboard;
    }

    /** Partie du jour de ce joueur, ou null s'il n'a pas encore joué. */
    public WhoRun find(String key) {
        WhoRun run = runs.get(id(today(), key));
        if (run != null) {
            synchronized (run) {
                refresh(run);
            }
        }
        return run;
    }

    /** Commence le défi (ou reprend celui du jour : un seul essai) et montre la première carte. */
    public WhoRun start(String key, String name) {
        LocalDate date = today();
        runs.keySet().removeIf(k -> !k.startsWith(date + "|") && !k.startsWith(date.minusDays(1) + "|"));
        WhoRun run = runs.computeIfAbsent(id(date, key), k -> new WhoRun(key, cleanName(name), daily.lineup(date)));
        synchronized (run) {
            if (run.index() < 0) {
                run.next(now());
            }
            refresh(run);
        }
        return run;
    }

    public Outcome buy(String key) {
        WhoRun run = require(key);
        synchronized (run) {
            Outcome o = run.buy(now());
            refresh(run);
            return o;
        }
    }

    /** « Call it! » : achat si le nom est juste, sinon l'image se précise (null). */
    public Outcome guess(String key, String name) {
        WhoRun run = require(key);
        synchronized (run) {
            Outcome o = run.guess(now(), name);
            refresh(run);
            return o;
        }
    }

    public Outcome pass(String key) {
        WhoRun run = require(key);
        synchronized (run) {
            Outcome o = run.pass(now());
            refresh(run);
            return o;
        }
    }

    public void next(String key) {
        WhoRun run = require(key);
        synchronized (run) {
            run.next(now());
            refresh(run);
        }
    }

    /**
     * Image de la carte en jeu : au palier demandé, sans jamais dépasser celui atteint (principe 2).
     * Le client ignore quelle carte est le jackpot : il demande le palier du prix, le serveur bride.
     */
    public byte[] image(String key, int index, int level) {
        WhoRun run = require(key);
        String url;
        int served;
        synchronized (run) {
            Instant now = now();
            if (run.index() != index || !run.live(now)) {
                throw new GameException("WHO_IMAGE_LOCKED", "Image pas encore disponible");
            }
            served = Math.max(0, Math.min(level, run.imageLevel(now)));
            url = run.current().image();
        }
        return blurrer.render(url, served);
    }

    public List<me.blindle.realtime.solo.DailyLeaderboard.Entry> top(int limit) {
        return leaderboard.top(today(), LEADERBOARD_ID, limit);
    }

    /** Fin de partie : score envoyé une seule fois au classement du jour. */
    private void refresh(WhoRun run) {
        run.live(now());
        if (run.finished() && run.rank() == null) {
            run.rank(leaderboard.submit(run.lineup().date(), LEADERBOARD_ID,
                    new DailyLeaderboard.Entry(run.name(), run.score())));
        }
    }

    private WhoRun require(String key) {
        WhoRun run = runs.get(id(today(), key));
        if (run == null) {
            throw new GameException("WHO_NOT_STARTED", "Lance d'abord le défi du jour");
        }
        return run;
    }

    private static String id(LocalDate date, String key) {
        return date + "|" + key;
    }

    static String cleanName(String name) {
        String n = name == null ? "" : name.strip().replaceAll("\\s+", " ");
        if (n.isEmpty() || n.length() > MAX_NAME) {
            throw new GameException("BAD_NAME", "Pseudo de 1 à " + MAX_NAME + " caractères");
        }
        return n;
    }
}
