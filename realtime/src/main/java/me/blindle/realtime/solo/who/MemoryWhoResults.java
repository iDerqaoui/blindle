package me.blindle.realtime.solo.who;

import me.blindle.realtime.solo.DailyLeaderboard;

import java.time.LocalDate;
import java.util.ArrayList;
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

    @Override
    public synchronized Optional<Result> find(LocalDate date, String key) {
        return Optional.ofNullable(results.get(date + "|" + key));
    }

    @Override
    public synchronized int save(Result result) {
        results.putIfAbsent(result.date() + "|" + result.key(), result);
        return rank(result);
    }

    @Override
    public synchronized int rank(Result result) {
        return 1 + (int) of(result.date()).stream()
                .filter(r -> r.score() > result.score()
                        || (r.score() == result.score() && r.goldLeft() > result.goldLeft()))
                .count();
    }

    @Override
    public synchronized List<DailyLeaderboard.Entry> top(LocalDate date, int limit) {
        return of(date).stream().sorted(RANKING).limit(limit)
                .map(r -> new DailyLeaderboard.Entry(r.name(), r.score())).toList();
    }

    @Override
    public synchronized int count(LocalDate date) {
        return of(date).size();
    }

    @Override
    public synchronized int streak(String key, LocalDate today) {
        List<LocalDate> days = results.values().stream().filter(r -> r.key().equals(key))
                .map(Result::date).sorted(Comparator.reverseOrder()).toList();
        return WhoResults.streakOf(days, today);
    }

    private List<Result> of(LocalDate date) {
        return new ArrayList<>(results.values().stream().filter(r -> r.date().equals(date)).toList());
    }
}
