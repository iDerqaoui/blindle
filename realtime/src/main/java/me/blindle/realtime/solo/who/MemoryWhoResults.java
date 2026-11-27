package me.blindle.realtime.solo.who;

import me.blindle.realtime.solo.DailyLeaderboard;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Résultats en mémoire (tests, ou serveur sans base). */
public class MemoryWhoResults implements WhoResults {

    static final Comparator<Result> RANKING = Comparator.comparingInt(Result::score).reversed()
            .thenComparing(Comparator.comparingInt(Result::goldLeft).reversed())
            .thenComparing(Result::finishedAt);

    private final Map<String, Result> results = new LinkedHashMap<>();

    private static String id(LocalDate date, String theme, String key) {
        return date + "|" + theme + "|" + key;
    }

    @Override
    public synchronized Optional<Result> find(LocalDate date, String theme, String key) {
        return Optional.ofNullable(results.get(id(date, theme, key)));
    }

    @Override
    public synchronized int save(Result result) {
        results.putIfAbsent(id(result.date(), result.theme(), result.key()), result);
        return rank(result);
    }

    @Override
    public synchronized int rank(Result result) {
        return 1 + (int) of(result.date(), result.theme()).stream()
                .filter(r -> r.score() > result.score()
                        || (r.score() == result.score() && r.goldLeft() > result.goldLeft()))
                .count();
    }

    @Override
    public synchronized List<DailyLeaderboard.Entry> top(LocalDate date, String theme, int limit) {
        return of(date, theme).stream().sorted(RANKING).limit(limit)
                .map(r -> new DailyLeaderboard.Entry(r.name(), r.score())).toList();
    }

    @Override
    public synchronized int count(LocalDate date, String theme) {
        return of(date, theme).size();
    }

    @Override
    public synchronized int streak(String key, LocalDate today) {
        List<LocalDate> days = results.values().stream().filter(r -> r.key().equals(key))
                .map(Result::date).distinct().sorted(Comparator.reverseOrder()).toList();
        return WhoResults.streakOf(days, today);
    }

    private List<Result> of(LocalDate date, String theme) {
        return results.values().stream().filter(r -> r.date().equals(date) && r.theme().equals(theme)).toList();
    }
}
