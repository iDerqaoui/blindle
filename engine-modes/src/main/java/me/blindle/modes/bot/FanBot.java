package me.blindle.modes.bot;

import me.blindle.core.GameRandom;
import me.blindle.core.model.Bid;
import me.blindle.core.model.Card;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Fan : a un tag favori (tiré par sa seed parmi les tags des cartes en jeu). Quand au moins la
 * moitié des cartes compatibles avec les indices le portent, il surenchérit dès le premier
 * indice jusqu'à 150 % ; sinon il attend le deuxième indice et plafonne à 90 %.
 */
@Component
public class FanBot implements BotStrategy {

    static final double FAN_CAP = 1.5;
    static final double NORMAL_CAP = 0.9;

    @Override
    public String profile() {
        return "fan";
    }

    @Override
    public String label() {
        return "Fan";
    }

    @Override
    public Optional<Bid> decide(BotView view) {
        double estimate = BotEstimator.estimate(view);
        String favorite = favoriteTag(view);
        List<Card> candidates = BotEstimator.candidates(view);
        long fans = candidates.stream().filter(c -> c.tags().contains(favorite)).count();
        if (favorite != null && !candidates.isEmpty() && 2 * fans >= candidates.size()) {
            return BotSupport.offer(view, FAN_CAP * estimate);
        }
        if (view.hintLevel() < 2) {
            return Optional.empty();
        }
        return BotSupport.offer(view, NORMAL_CAP * estimate);
    }

    /** Tag favori, stable pour toute la partie (null si aucune carte n'a de tag). */
    static String favoriteTag(BotView view) {
        TreeSet<String> tags = new TreeSet<>();
        view.unseen().forEach(c -> tags.addAll(c.tags()));
        if (tags.isEmpty()) {
            return null;
        }
        return GameRandom.derive(view.botSeed(), "favorite-tag").pick(List.copyOf(tags));
    }
}
