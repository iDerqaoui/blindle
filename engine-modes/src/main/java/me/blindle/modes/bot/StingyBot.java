package me.blindle.modes.bot;

import me.blindle.core.model.Bid;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Radin : attend le dernier indice et plafonne à 80 % de son estimation. */
@Component
public class StingyBot implements BotStrategy {

    static final double CAP = 0.8;

    @Override
    public String profile() {
        return "stingy";
    }

    @Override
    public String label() {
        return "Radin";
    }

    @Override
    public Optional<Bid> decide(BotView view) {
        if (!view.lastHint()) {
            return Optional.empty();
        }
        return BotSupport.offer(view, CAP * BotEstimator.estimate(view));
    }
}
