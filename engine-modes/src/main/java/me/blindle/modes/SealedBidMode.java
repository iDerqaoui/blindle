package me.blindle.modes;

import me.blindle.core.GameRandom;
import me.blindle.core.model.Bid;
import me.blindle.core.model.GameState;
import me.blindle.core.model.LotResult;
import me.blindle.core.model.LotState;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * {@code sealed} : offres secrètes simultanées. La plus haute gagne et paie son offre ;
 * égalité départagée par la seed. Résolu dès que tous les joueurs éligibles ont misé
 * ou passé, sinon à l'heure de fin.
 */
@Component
public class SealedBidMode implements BidMode {

    public static final String ID = "sealed";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean bidsVisible() {
        return false;
    }

    @Override
    public boolean isComplete(GameState state) {
        LotState lot = state.lot();
        return lot != null && state.eligibleBidders().stream().allMatch(lot::hasActed);
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
}
