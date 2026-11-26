package me.blindle.modes;

import me.blindle.core.GameException;
import me.blindle.core.GameRandom;
import me.blindle.core.model.Bid;
import me.blindle.core.model.GameState;
import me.blindle.core.model.LotResult;
import me.blindle.core.model.LotState;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * {@code open} : offres visibles de tous ; chaque offre doit dépasser la meilleure en cours.
 * Le meilleur enchérisseur ne peut pas passer. Résolu dès que tous les autres joueurs éligibles
 * ont passé, sinon à l'heure de fin (que la règle anti-snipe peut repousser).
 */
@Component
public class OpenBidMode implements BidMode {

    public static final String ID = "open";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean bidsVisible() {
        return true;
    }

    @Override
    public void validateBid(GameState state, Bid bid) {
        Optional<Bid> leader = leader(state.lot());
        if (leader.isPresent() && bid.amount() <= leader.get().amount()) {
            throw new GameException("BID_TOO_LOW", "Il faut dépasser " + leader.get().amount());
        }
    }

    @Override
    public void validatePass(GameState state, String playerId) {
        if (leader(state.lot()).filter(b -> b.playerId().equals(playerId)).isPresent()) {
            throw new GameException("LEADER_CANNOT_PASS", "Tu mènes l'enchère : tu ne peux pas passer");
        }
    }

    @Override
    public boolean isComplete(GameState state) {
        LotState lot = state.lot();
        if (lot == null) {
            return false;
        }
        String leaderId = leader(lot).map(Bid::playerId).orElse(null);
        List<String> others = state.eligibleBidders().stream().filter(id -> !id.equals(leaderId)).toList();
        return others.stream().allMatch(lot.passes()::contains);
    }

    @Override
    public Optional<LotResult> resolve(LotState lot, GameRandom random) {
        if (lot.bids().isEmpty()) {
            return Optional.empty();
        }
        int max = lot.bids().stream().mapToInt(Bid::amount).max().orElseThrow();
        List<Bid> top = lot.bids().stream().filter(b -> b.amount() == max).toList();
        Bid winner = top.size() == 1 ? top.get(0) : random.pick(top);
        return Optional.of(new LotResult(winner.playerId(), winner.amount()));
    }

    static Optional<Bid> leader(LotState lot) {
        return lot.bids().stream().max(Comparator.comparingInt(Bid::amount));
    }
}
