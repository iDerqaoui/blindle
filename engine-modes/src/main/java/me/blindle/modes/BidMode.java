package me.blindle.modes;

import me.blindle.core.BidResolver;
import me.blindle.core.GameRandom;
import me.blindle.core.model.GameState;
import me.blindle.core.model.LotResult;
import me.blindle.core.model.LotState;

import java.util.Optional;

/**
 * Mode d'enchère : décide quand un palier peut se clore et qui le remporte.
 * Sans état : tout est lu dans {@link LotState}.
 */
public interface BidMode extends BidResolver {

    /** Identifiant référencé par les presets, ex. {@code sealed}. */
    String id();

    /** Les montants des offres adverses sont-ils visibles avant résolution ? */
    boolean bidsVisible();

    /** Le palier peut-il être résolu avant l'heure de fin ? */
    boolean isComplete(GameState state);

    /** Contrôle propre au mode avant d'enregistrer une offre ; lève GameException si refusée. */
    default void validateBid(GameState state, me.blindle.core.model.Bid bid) {
    }

    /** Contrôle propre au mode avant d'enregistrer une passe ; lève GameException si refusée. */
    default void validatePass(GameState state, String playerId) {
    }

    /**
     * Joueur qui a pioché le lot courant et seul à en voir la carte, ou null si le mode n'a pas
     * de piocheur (toutes les cartes sont alors cachées à tous jusqu'à la révélation).
     */
    default String drawerId(GameState state) {
        return null;
    }

    /** La carte gagnée est-elle posée d'office dans l'équipe (sans choix de slot) ? */
    default boolean autoPlace() {
        return false;
    }

    @Override
    Optional<LotResult> resolve(LotState lot, GameRandom random);
}
