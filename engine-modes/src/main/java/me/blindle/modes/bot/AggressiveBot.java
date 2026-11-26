package me.blindle.modes.bot;

import me.blindle.core.model.Bid;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Agressif : mise dès le premier indice, jusqu'à 120 % de son estimation. */
@Component
public class AggressiveBot implements BotStrategy {

    static final double CAP = 1.2;

    @Override
    public String profile() {
        return "aggressive";
    }

    @Override
    public String label() {
        return "Agressif";
    }

    @Override
    public Optional<Bid> decide(BotView view) {
        return BotSupport.offer(view, CAP * BotEstimator.estimate(view));
    }
}
