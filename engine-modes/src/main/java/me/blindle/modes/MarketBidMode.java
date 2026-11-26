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
 * {@code market} (solo, défi du jour) : chaque offre secrète affronte un prix du marché caché.
 * Une offre au moins égale au prix du marché remporte le lot et paie son montant ; sinon
 * l'indice suivant est révélé. Entre plusieurs offres gagnantes, la plus haute l'emporte
 * (égalité départagée par la seed).
 * <p>
 * Prix du marché = valeur du lot ({@link CardValuation}) × ratio croissant avec l'indice
 * (de {@link #FIRST_RATIO} au premier palier à {@link #LAST_RATIO} au dernier) × bruit seedé
 * de ±{@link #NOISE} ; jamais sous le prix plancher. Miser tôt coûte donc moins cher.
 */
@Component
public class MarketBidMode implements BidMode {

    public static final String ID = "market";

    static final double FIRST_RATIO = 0.5;
    static final double LAST_RATIO = 0.9;
    static final double NOISE = 0.15;

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
        int market = marketPrice(lot, random);
        List<Bid> winning = lot.bids().stream().filter(b -> b.amount() >= market).toList();
        if (winning.isEmpty()) {
            return Optional.empty();
        }
        int max = winning.stream().mapToInt(Bid::amount).max().orElseThrow();
        List<Bid> top = winning.stream().filter(b -> b.amount() == max).toList();
        Bid winner = top.size() == 1 ? top.get(0) : random.pick(top);
        return Optional.of(new LotResult(winner.playerId(), winner.amount()));
    }

    /** Prix du marché du palier courant. Consomme un tirage de {@code random} (le bruit). */
    static int marketPrice(LotState lot, GameRandom random) {
        int value = CardValuation.sum(lot.cards());
        int levels = lot.maxHintLevel();
        double progress = levels <= 1 ? 1.0 : (double) (lot.hintLevel() - 1) / (levels - 1);
        double ratio = FIRST_RATIO + (LAST_RATIO - FIRST_RATIO) * Math.max(0, Math.min(1, progress));
        double noise = 1 - NOISE + 2 * NOISE * random.nextDouble();
        return Math.max(lot.floorPrice(), (int) Math.round(value * ratio * noise));
    }
}
