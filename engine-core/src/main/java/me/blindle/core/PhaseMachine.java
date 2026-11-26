package me.blindle.core;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Table des transitions de phase autorisées. Toute autre transition est refusée.
 */
public final class PhaseMachine {

    private static final Map<Phase, Set<Phase>> ALLOWED = new EnumMap<>(Phase.class);

    static {
        ALLOWED.put(Phase.LOBBY, EnumSet.of(Phase.STARTED));
        ALLOWED.put(Phase.STARTED, EnumSet.of(Phase.DRAW, Phase.VERDICT));
        ALLOWED.put(Phase.DRAW, EnumSet.of(Phase.HINT));
        ALLOWED.put(Phase.HINT, EnumSet.of(Phase.BIDDING));
        // Après les enchères : gagnant, indice suivant, ou lot invendu (round suivant / verdict).
        ALLOWED.put(Phase.BIDDING, EnumSet.of(Phase.AWARDED, Phase.HINT, Phase.DRAW, Phase.VERDICT));
        ALLOWED.put(Phase.AWARDED, EnumSet.of(Phase.REVEALED));
        ALLOWED.put(Phase.REVEALED, EnumSet.of(Phase.SLOT));
        ALLOWED.put(Phase.SLOT, EnumSet.of(Phase.DRAW, Phase.VERDICT));
        ALLOWED.put(Phase.VERDICT, EnumSet.of(Phase.FINISHED));
        ALLOWED.put(Phase.FINISHED, EnumSet.noneOf(Phase.class));
    }

    public boolean canTransition(Phase from, Phase to) {
        return ALLOWED.get(from).contains(to);
    }

    /** Vérifie la transition et renvoie la phase cible, ou lève {@link IllegalPhaseTransitionException}. */
    public Phase transition(Phase from, Phase to) {
        if (!canTransition(from, to)) {
            throw new IllegalPhaseTransitionException(from, to);
        }
        return to;
    }

    /** Lève {@link GameException} {@code WRONG_PHASE} si la phase courante n'est pas attendue. */
    public void requirePhase(Phase actual, Phase... expected) {
        for (Phase p : expected) {
            if (p == actual) {
                return;
            }
        }
        throw new GameException("WRONG_PHASE", "Action impossible en phase " + actual);
    }
}
