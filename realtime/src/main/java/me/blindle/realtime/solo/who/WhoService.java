package me.blindle.realtime.solo.who;

import me.blindle.core.GameException;
import me.blindle.realtime.solo.DailyLeaderboard;
import me.blindle.realtime.solo.who.WhoRun.Outcome;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Défi « Who's That? » : un par univers et par jour, un essai par joueur ({@code key} : compte connecté
 * ou cookie du navigateur). La partie en cours vit en mémoire ; le résultat final est enregistré
 * ({@link WhoResults}) pour le classement, l'essai unique et la série de jours, même après un redémarrage.
 */
@Component
public class WhoService {

    static final int MAX_NAME = 20;

    private final WhoDaily daily;
    private final WhoResults results;
    private final Blurrer blurrer;
    private final Clock clock;
    private final ZoneId zone;
    /** Parties en cours : « date|univers|joueur ». */
    private final Map<String, WhoRun> runs = new ConcurrentHashMap<>();

    public WhoService(WhoDaily daily, WhoResults results, Blurrer blurrer, Clock clock,
                      @Value("${blindle.daily.zone:Europe/Paris}") String zone) {
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

    public WhoLineup lineup(String theme) {
        return daily.lineup(today(), theme);
    }

    public int players(String theme) {
        return results.count(today(), theme);
    }

    public List<DailyLeaderboard.Entry> top(String theme, int limit) {
        return results.top(today(), theme, limit);
    }

    /** Résultat enregistré aujourd'hui dans cet univers (même si le serveur a redémarré depuis). */
    public record Mine(int score, int rank, int streak, String share) {
    }

    public Optional<Mine> mine(String theme, String key) {
        if (key == null) {
            return Optional.empty();
        }
        LocalDate date = today();
        return results.find(date, theme, key)
                .map(r -> new Mine(r.score(), results.rank(r), results.streak(key, date), r.share()));
    }

    /** Partie du jour de ce joueur dans cet univers, ou null s'il n'en a pas en cours. */
    public WhoRun find(String theme, String key) {
        if (key == null) {
            return null;
        }
        WhoRun run = runs.get(id(today(), theme, key));
        if (run != null) {
            synchronized (run) {
                refresh(run);
            }
        }
        return run;
    }

    /** Commence le défi de cet univers (ou reprend celui en cours : un seul essai) et montre la première carte. */
    public WhoRun start(String theme, String key, String name) {
        LocalDate date = today();
        runs.keySet().removeIf(k -> !k.startsWith(date + "|") && !k.startsWith(date.minusDays(1) + "|"));
        String id = id(date, theme, key);
        if (!runs.containsKey(id) && results.find(date, theme, key).isPresent()) {
            throw new GameException("WHO_FINISHED", "Tu as déjà joué le défi du jour de cet univers");
        }
        WhoLineup lineup = daily.lineup(date, theme);
        WhoRun run = runs.computeIfAbsent(id, k -> new WhoRun(key, cleanName(name), lineup));
        synchronized (run) {
            if (run.index() < 0) {
                run.next(now());
            }
            refresh(run);
        }
        return run;
    }

    public Outcome buy(String theme, String key) {
        WhoRun run = require(theme, key);
        synchronized (run) {
            Outcome o = run.buy(now());
            refresh(run);
            return o;
        }
    }

    /** « Call it! » : achat si le nom est juste, sinon l'image se précise (null). */
    public Outcome guess(String theme, String key, String name) {
        WhoRun run = require(theme, key);
        synchronized (run) {
            Outcome o = run.guess(now(), name);
            refresh(run);
            return o;
        }
    }

    public Outcome pass(String theme, String key) {
        WhoRun run = require(theme, key);
        synchronized (run) {
            Outcome o = run.pass(now());
            refresh(run);
            return o;
        }
    }

    public void next(String theme, String key) {
        WhoRun run = require(theme, key);
        synchronized (run) {
            run.next(now());
            refresh(run);
        }
    }

    /**
     * Image de la carte en jeu : au palier demandé, sans jamais dépasser celui atteint (principe 2).
     * Le client ignore quelle carte est le jackpot : il demande le palier du prix, le serveur bride.
     */
    public byte[] image(String theme, String key, int index, int level) {
        WhoRun run = require(theme, key);
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

    /** Fin de partie : série du jour, puis résultat enregistré une seule fois. */
    private void refresh(WhoRun run) {
        run.live(now());
        if (run.finished() && run.rank() == null) {
            WhoLineup lineup = run.lineup();
            LocalDate date = lineup.date();
            // Série : jours joués jusqu'à hier, plus aujourd'hui (déjà compté si un autre univers est fait).
            int streak = Math.max(results.streak(run.key(), date), results.streak(run.key(), date.minusDays(1)) + 1);
            run.streak(streak);
            run.rank(results.save(new WhoResults.Result(date, lineup.themeId(), run.key(), run.name(),
                    lineup.universe(), run.score(), run.budget(), run.share(), now())));
        }
    }

    private WhoRun require(String theme, String key) {
        WhoRun run = key == null ? null : runs.get(id(today(), theme, key));
        if (run == null) {
            throw new GameException("WHO_NOT_STARTED", "Lance d'abord le défi du jour");
        }
        return run;
    }

    private static String id(LocalDate date, String theme, String key) {
        return date + "|" + theme + "|" + key;
    }

    static String cleanName(String name) {
        String n = name == null ? "" : name.strip().replaceAll("\\s+", " ");
        if (n.isEmpty() || n.length() > MAX_NAME) {
            throw new GameException("BAD_NAME", "Pseudo de 1 à " + MAX_NAME + " caractères");
        }
        return n;
    }
}
