package me.blindle.realtime.solo.who;

import me.blindle.realtime.solo.DailyLeaderboard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Résultats du défi « Who's That? » : un par joueur et par jour. Classement : score décroissant, puis
 * or restant, puis premier arrivé. En base en production ({@link JpaWhoResults}), en mémoire en test.
 */
public interface WhoResults {

    /** @param key joueur : « u:… » (compte) ou « c:… » (navigateur) */
    record Result(LocalDate date, String key, String name, String universe, int score, int goldLeft, String share,
                  Instant finishedAt) {
    }

    Optional<Result> find(LocalDate date, String key);

    /** Enregistre le résultat (une seule fois par jour et par joueur) ; renvoie le rang obtenu. */
    int save(Result result);

    int rank(Result result);

    List<DailyLeaderboard.Entry> top(LocalDate date, int limit);

    int count(LocalDate date);

    /** Jours consécutifs joués jusqu'à {@code today} inclus (0 si pas joué ce jour-là). */
    int streak(String key, LocalDate today);

    /** Série à partir des jours joués, du plus récent au plus ancien. */
    static int streakOf(List<LocalDate> daysDesc, LocalDate today) {
        int streak = 0;
        LocalDate expected = today;
        for (LocalDate d : daysDesc) {
            if (d.isAfter(expected)) {
                continue;
            }
            if (!d.equals(expected)) {
                break;
            }
            streak++;
            expected = expected.minusDays(1);
        }
        return streak;
    }
}
