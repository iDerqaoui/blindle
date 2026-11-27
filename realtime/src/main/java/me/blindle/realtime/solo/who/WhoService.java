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
 * navigateur). La partie en cours vit en mémoire ; le résultat final est enregistré ({@link WhoResults})
 * pour le classement, l'essai unique et la série de jours, même après un redémarrage.
 */
@Component
public class WhoService {

    static final int MAX_NAME = 20;

    private final WhoDaily daily;
    private final WhoResults results;
    private final Blurrer blurrer;
    private final Clock clock;
    private final ZoneId zone;
    private final Map<String, WhoRun> runs = new ConcurrentHashMap<>();

    public WhoService(WhoDaily daily, WhoResults results, Blurrer blurrer, Clock clock,
                      @org.springframework.beans.factory.annotation.Value("${blindle.daily.zone:Europe/Paris}") String zone) {
        this.daily = daily;
        this.results = results;
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

    public int players() {
        return results.count(today());
    }

    /** Résultat enregistré aujourd'hui pour ce joueur (même si le serveur a redémarré depuis). */
    public record Mine(int score, int rank, int streak, String share) {
    }

    public java.util.Optional<Mine> mine(String key) {
        if (key == null) {
            return java.util.Optional.empty();
        }
        LocalDate date = today();
        return results.find(date, key).map(r -> new Mine(r.score(), results.rank(r), results.streak(key, date), r.share()));
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
        if (!runs.containsKey(id(date, key)) && results.find(date, key).isPresent()) {
            throw new GameException("WHO_FINISHED", "Tu as déjà joué le défi du jour");
        }
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

    public List<DailyLeaderboard.Entry> top(int limit) {
        return results.top(today(), limit);
    }

    /** Fin de partie : série du jour, puis résultat enregistré une seule fois. */
    private void refresh(WhoRun run) {
        run.live(now());
        if (run.finished() && run.rank() == null) {
            LocalDate date = run.lineup().date();
            run.streak(results.streak(run.key(), date.minusDays(1)) + 1);
            run.rank(results.save(new WhoResults.Result(date, run.key(), run.name(), run.lineup().universe(),
                    run.score(), run.budget(), run.share(), now())));
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
