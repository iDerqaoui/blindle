package me.blindle.realtime.solo;

import me.blindle.core.model.Card;
import me.blindle.core.model.GameState;
import me.blindle.core.model.PlayerState;
import me.blindle.modes.CardValuation;
import me.blindle.modes.ValueVerdictMode;
import me.blindle.realtime.room.RoomSnapshot;
import me.blindle.realtime.room.RoundOutcome;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Résultat du défi du jour à partager, sans rien dévoiler des cartes : une ligne par round,
 * une case par palier d'indice.
 * <ul>
 *   <li>⬜ palier joué sans achat ;</li>
 *   <li>🟩 achat rentable (valeur ≥ prix payé), 🟥 achat à perte ;</li>
 *   <li>⬛ palier non atteint.</li>
 * </ul>
 */
public final class DailyShare {

    static final String PLAYED = "⬜";
    static final String GOOD = "🟩";
    static final String BAD = "🟥";
    static final String UNREACHED = "⬛";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private DailyShare() {
    }

    /** Texte à partager pour le joueur, ou null si ce n'est pas un défi du jour terminé. */
    public static String text(RoomSnapshot r, String playerId) {
        GameState s = r.state();
        if (r.dailyDate() == null || s.result() == null) {
            return null;
        }
        PlayerState me = s.player(playerId).orElse(null);
        if (me == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Blindle · Défi du jour ").append(DATE.format(r.dailyDate())).append('\n');
        int score = ValueVerdictMode.score(s, me);
        sb.append(r.setup().theme().name()).append(" · score ").append(score > 0 ? "+" : "").append(score)
                .append('\n');
        for (RoundOutcome o : r.history()) {
            sb.append(row(s, o, playerId)).append('\n');
        }
        sb.append("blindle.app");
        return sb.toString();
    }

    static String row(GameState s, RoundOutcome o, String playerId) {
        boolean mine = playerId.equals(o.winnerId());
        StringBuilder sb = new StringBuilder();
        for (int level = 1; level <= o.maxHintLevel(); level++) {
            if (level > o.hintLevel()) {
                sb.append(UNREACHED);
            } else if (mine && level == o.hintLevel()) {
                List<Card> cards = o.cardIds().stream().map(id -> s.card(id).orElseThrow()).toList();
                sb.append(CardValuation.sum(cards) >= o.price() ? GOOD : BAD);
            } else {
                sb.append(PLAYED);
            }
        }
        return sb.toString();
    }
}
