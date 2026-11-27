package me.blindle.realtime.solo.who;

import me.blindle.realtime.solo.DailyLeaderboard;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Résultats du défi en base (table {@code who_result}) : survivent aux redémarrages. */
@Component
public class JpaWhoResults implements WhoResults {

    private static final int STREAK_LOOKBACK = 2000;

    private final WhoResultRepository repo;

    public JpaWhoResults(WhoResultRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Result> find(LocalDate date, String theme, String key) {
        return repo.findById(new WhoResultRow.Key(date, theme, key)).map(WhoResultRow::toResult);
    }

    @Override
    @Transactional
    public int save(Result result) {
        if (!repo.existsById(new WhoResultRow.Key(result.date(), result.theme(), result.key()))) {
            repo.save(WhoResultRow.of(result));
        }
        return rank(result);
    }

    @Override
    @Transactional(readOnly = true)
    public int rank(Result result) {
        return 1 + (int) repo.countBetter(result.date(), result.theme(), result.score(), result.goldLeft());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyLeaderboard.Entry> top(LocalDate date, String theme, int limit) {
        return repo.ranking(date, theme, PageRequest.of(0, limit)).stream()
                .map(r -> new DailyLeaderboard.Entry(r.name, r.score)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public int count(LocalDate date, String theme) {
        return (int) repo.countByPlayedOnAndTheme(date, theme);
    }

    @Override
    @Transactional(readOnly = true)
    public int streak(String key, LocalDate today) {
        List<LocalDate> days = repo.days(key, PageRequest.of(0, STREAK_LOOKBACK)).stream().distinct().toList();
        return WhoResults.streakOf(days, today);
    }
}
