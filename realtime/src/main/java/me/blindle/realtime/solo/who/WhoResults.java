package me.blindle.realtime.solo.who;

import me.blindle.realtime.solo.DailyLeaderboard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Résultats du défi « Who's That? » : un par joueur, par univers et par jour. Classement d'un univers :
 * score décroissant, puis or restant, puis premier arrivé. La série compte les jours où le joueur a
 * joué au moins un univers. En base en production ({@link JpaWhoResults}), en mémoire en test.
 */
public interface WhoResults {

    /**
     * @param theme univers (identifiant du thème)
     * @param key   joueur : « u:… » (compte) ou « c:… » (navigateur)
     */
    record Result(LocalDate date, String theme, String key, String name, String universe, int score, int goldLeft,
                  String share, Instant finishedAt) {
    }

    Optional<Result> find(LocalDate date, String theme, String key);

    /** Enregistre le résultat (une seule fois par jour, univers et joueur) ; renvoie le rang obtenu. */
    int save(Result result);

    int rank(Result result);

    List<DailyLeaderboard.Entry> top(LocalDate date, String theme, int limit);

    int count(LocalDate date, String theme);

    /** Jours consécutifs joués (tous univers confondus) jusqu'à {@code today} inclus. */
    int streak(String key, LocalDate today);

    /** Série à partir des jours joués, du plus récent au plus ancien (doublons tolérés). */
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
