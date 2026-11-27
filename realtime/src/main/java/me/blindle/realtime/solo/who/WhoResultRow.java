package me.blindle.realtime.solo.who;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Ligne de la table {@code who_result} : résultat d'un joueur, pour un univers et un jour. */
@Entity(name = "WhoResultRow")
@Table(name = "who_result")
@IdClass(WhoResultRow.Key.class)
public class WhoResultRow {

    @Id
    @Column(name = "played_on")
    LocalDate playedOn;

    @Id
    @Column(name = "theme")
    String theme;

    @Id
    @Column(name = "player_key")
    String playerKey;

    @Column(nullable = false)
    String name;

    @Column(nullable = false)
    String universe;

    @Column(nullable = false)
    int score;

    @Column(name = "gold_left", nullable = false)
    int goldLeft;

    @Column(nullable = false)
    String share;

    @Column(name = "finished_at", nullable = false)
    Instant finishedAt;

    protected WhoResultRow() {
    }

    static WhoResultRow of(WhoResults.Result r) {
        WhoResultRow row = new WhoResultRow();
        row.playedOn = r.date();
        row.theme = r.theme();
        row.playerKey = r.key();
        row.name = r.name();
        row.universe = r.universe();
        row.score = r.score();
        row.goldLeft = r.goldLeft();
        row.share = r.share();
        row.finishedAt = r.finishedAt();
        return row;
    }

    WhoResults.Result toResult() {
        return new WhoResults.Result(playedOn, theme, playerKey, name, universe, score, goldLeft, share, finishedAt);
    }

    public static class Key implements Serializable {
        LocalDate playedOn;
        String theme;
        String playerKey;

        protected Key() {
        }

        Key(LocalDate playedOn, String theme, String playerKey) {
            this.playedOn = playedOn;
            this.theme = theme;
            this.playerKey = playerKey;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(playedOn, k.playedOn) && Objects.equals(theme, k.theme)
                    && Objects.equals(playerKey, k.playerKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(playedOn, theme, playerKey);
        }
    }
}
