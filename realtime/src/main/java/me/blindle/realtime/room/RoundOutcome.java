package me.blindle.realtime.room;

import java.util.List;

/**
 * Issue d'un round, relevée par la room à partir des événements du moteur.
 *
 * @param winnerId     gagnant, ou null si le lot est resté invendu
 * @param hintLevel    palier auquel le lot a été vendu (ou dernier palier joué s'il est invendu)
 * @param maxHintLevel nombre de paliers du lot
 * @param price        prix payé (0 si invendu)
 */
public record RoundOutcome(int round, List<String> cardIds, String winnerId, int hintLevel,
                           int maxHintLevel, int price) {

    public RoundOutcome {
        cardIds = List.copyOf(cardIds);
    }

    public boolean sold() {
        return winnerId != null;
    }
}
