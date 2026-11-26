package me.blindle.modes.bot;

import me.blindle.core.model.Bid;

import java.util.Optional;

/**
 * Personnalité d'un bot (§7). Reçoit les mêmes informations qu'un humain ({@link BotView}) et
 * renvoie une offre, ou vide pour passer. Sans état : tout aléa dérive de {@link BotView#botSeed()}.
 */
public interface BotStrategy {

    /** Identifiant du profil, ex. {@code aggressive}. */
    String profile();

    /** Nom affiché, en français. */
    String label();

    Optional<Bid> decide(BotView view);
}
