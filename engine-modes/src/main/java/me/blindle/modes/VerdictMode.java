package me.blindle.modes;

import me.blindle.core.GameException;
import me.blindle.core.model.GameResult;
import me.blindle.core.model.GameState;

import java.util.List;
import java.util.Set;

/** Mode de verdict : désigne la meilleure équipe en fin de partie. */
public interface VerdictMode {

    /** Identifiant référencé par les presets, ex. {@code vote}. */
    String id();

    /** Champs de thème requis (ex. {@code stats}) ; le mode est désactivé s'ils manquent. */
    Set<String> requiredThemeFields();

    /** Ce mode attend-il des votes des joueurs ? */
    default boolean usesVotes() {
        return false;
    }

    /** Vérifie un vote avant de l'enregistrer ; lève {@link GameException} s'il est invalide. */
    default void validateVote(GameState state, Vote vote, List<Vote> existing) {
        throw new GameException("NO_VOTES", "Ce mode de verdict ne se joue pas au vote");
    }

    /** Tous les votes attendus sont-ils reçus ? */
    default boolean votesComplete(GameState state, List<Vote> votes) {
        return true;
    }

    GameResult compute(GameState state, List<Vote> votes);
}
