package me.blindle.modes.bot;

import me.blindle.core.model.Bid;

import java.util.Optional;

/** Mise commune à tous les profils, à partir d'un plafond. */
final class BotSupport {

    /** Pas de surenchère et arrondi des offres. */
    static final int STEP = 10;

    private BotSupport() {
    }

    /**
     * Offre au plus {@code cap} (et au plus le budget) : en enchère visible, juste au-dessus du
     * meneur ; sinon le plafond, arrondi à la dizaine. Vide (passe) si le plancher n'est pas atteignable.
     */
    static Optional<Bid> offer(BotView v, double cap) {
        int max = Math.min((int) Math.floor(cap), v.budget());
        int amount;
        if (v.bidsVisible()) {
            int leader = v.leader().map(Bid::amount).orElse(0);
            amount = Math.max(v.floorPrice(), leader + STEP);
        } else {
            amount = Math.max(v.floorPrice(), max / STEP * STEP);
        }
        if (amount <= 0 || amount > max) {
            return Optional.empty();
        }
        return Optional.of(new Bid(v.botId(), amount));
    }
}
