package me.blindle.realtime.solo.who;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/** Accès à la table {@code who_result}. */
public interface WhoResultRepository extends JpaRepository<WhoResultRow, WhoResultRow.Key> {

    long countByPlayedOnAndTheme(LocalDate playedOn, String theme);

    @Query("select count(r) from WhoResultRow r where r.playedOn = :d and r.theme = :theme and "
            + "(r.score > :score or (r.score = :score and r.goldLeft > :gold))")
    long countBetter(@Param("d") LocalDate date, @Param("theme") String theme, @Param("score") int score, @Param("gold") int goldLeft);

    @Query("select r from WhoResultRow r where r.playedOn = :d and r.theme = :theme "
            + "order by r.score desc, r.goldLeft desc, r.finishedAt asc")
    List<WhoResultRow> ranking(@Param("d") LocalDate date, @Param("theme") String theme, Pageable page);

    @Query("select r.playedOn from WhoResultRow r where r.playerKey = :key order by r.playedOn desc")
    List<LocalDate> days(@Param("key") String key, Pageable page);
}
