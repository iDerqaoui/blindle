package me.blindle.modes.bot;

import me.blindle.core.model.Bid;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Bluffeur : fait monter les prix puis se retire. En enchère visible, il surenchérit jusqu'à
 * son estimation tant que ce n'est pas le dernier indice, puis passe. En enchère secrète, il
 * tente un coup à 70 % au premier indice et passe ensuite.
 */
@Component
public class BlufferBot implements BotStrategy {

    static final double PUMP_CAP = 1.0;
    static final double SEALED_CAP = 0.7;

    @Override
    public String profile() {
        return "bluffer";
    }

    @Override
    public String label() {
        return "Bluffeur";
    }

    @Override
    public Optional<Bid> decide(BotView view) {
        if (view.lastHint()) {
            return Optional.empty();
        }
        double estimate = BotEstimator.estimate(view);
        if (view.bidsVisible()) {
            return BotSupport.offer(view, PUMP_CAP * estimate);
        }
        return view.hintLevel() == 1 ? BotSupport.offer(view, SEALED_CAP * estimate) : Optional.empty();
    }
}
