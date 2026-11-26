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

    @Override
    Optional<LotResult> resolve(LotState lot, GameRandom random);
}
