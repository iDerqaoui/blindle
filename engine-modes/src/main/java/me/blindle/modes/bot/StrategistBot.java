package me.blindle.modes.bot;

import me.blindle.core.model.Bid;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Stratège : garde de quoi remplir ses slots vides (le plancher courant pour chacun des autres
 * slots libres). Mise à partir du milieu des indices jusqu'à son estimation ; s'il reste à peine
 * assez de rounds pour compléter son équipe, il mise dès le premier indice jusqu'à 110 %.
 */
@Component
public class StrategistBot implements BotStrategy {

    static final double CAP = 1.0;
    static final double URGENT_CAP = 1.1;

    @Override
    public String profile() {
        return "strategist";
    }

    @Override
    public String label() {
        return "Stratège";
    }

    @Override
    public Optional<Bid> decide(BotView view) {
        int reserve = Math.max(0, view.freeSlots() - view.lotSize()) * view.floorPrice();
        double spendable = view.budget() - reserve;
        boolean urgent = view.roundsLeft() <= view.freeSlots();
        double estimate = BotEstimator.estimate(view);
        if (urgent) {
            return BotSupport.offer(view, Math.min(spendable, URGENT_CAP * estimate));
        }
        if (2 * view.hintLevel() < view.maxHintLevel()) {
            return Optional.empty();
        }
        return BotSupport.offer(view, Math.min(spendable, CAP * estimate));
    }
}
