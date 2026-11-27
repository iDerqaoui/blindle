package me.blindle.realtime.solo.who;

import me.blindle.realtime.solo.DailyLeaderboard;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Résultats du défi en base (table {@code who_result}, migration V4) : survivent aux redémarrages. */
@Component
public class JpaWhoResults implements WhoResults {

    private static final int STREAK_LOOKBACK = 400;

    private final WhoResultRepository repo;

    public JpaWhoResults(WhoResultRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Result> find(LocalDate date, String key) {
        return repo.findById(new WhoResultRow.Key(date, key)).map(WhoResultRow::toResult);
    }

    @Override
    @Transactional
    public int save(Result result) {
        if (!repo.existsById(new WhoResultRow.Key(result.date(), result.key()))) {
            repo.save(WhoResultRow.of(result));
        }
        return rank(result);
    }

    @Override
    @Transactional(readOnly = true)
    public int rank(Result result) {
        return 1 + (int) repo.countBetter(result.date(), result.score(), result.goldLeft());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyLeaderboard.Entry> top(LocalDate date, int limit) {
        return repo.ranking(date, PageRequest.of(0, limit)).stream()
                .map(r -> new DailyLeaderboard.Entry(r.name, r.score)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public int count(LocalDate date) {
        return (int) repo.countByPlayedOn(date);
    }

    @Override
    @Transactional(readOnly = true)
    public int streak(String key, LocalDate today) {
        return WhoResults.streakOf(repo.days(key, PageRequest.of(0, STREAK_LOOKBACK)), today);
    }
}
