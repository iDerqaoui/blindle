package me.blindle.modes.bot;

import me.blindle.core.GameException;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** Profils de bots disponibles, par identifiant. */
@Component
public class BotCatalog {

    private final List<BotStrategy> strategies;

    public BotCatalog(List<BotStrategy> strategies) {
        this.strategies = strategies.stream().sorted(Comparator.comparing(BotStrategy::profile)).toList();
    }

    public List<BotStrategy> all() {
        return strategies;
    }

    public BotStrategy require(String profile) {
        return strategies.stream().filter(s -> s.profile().equals(profile)).findFirst()
                .orElseThrow(() -> new GameException("UNKNOWN_BOT", "Profil de bot inconnu : " + profile));
    }
}
