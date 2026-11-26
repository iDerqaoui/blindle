package me.blindle.core;

import me.blindle.core.model.LotResult;
import me.blindle.core.model.GameState;
import me.blindle.core.model.LotState;

import java.util.Optional;

/**
 * Résolution d'un palier d'enchère (implémentée par les modes d'enchère).
 * Vide = pas de gagnant sur ce palier.
 */
@FunctionalInterface
public interface BidResolver {

    Optional<LotResult> resolve(LotState lot, GameRandom random);

    /**
     * Vendeur du lot courant (ex. le piocheur), ou null s'il n'y en a pas (la banque vend).
     * Transmis aux règles par {@code LotWon} et {@code LotDiscarded}.
     */
    default String sellerId(GameState state) {
        return null;
    }
}
