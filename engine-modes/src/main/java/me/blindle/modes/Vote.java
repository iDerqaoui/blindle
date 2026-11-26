package me.blindle.modes;

import java.util.Objects;

/** Vote d'un joueur pour l'équipe d'un autre joueur. */
public record Vote(String voterId, String teamOwnerId) {

    public Vote {
        Objects.requireNonNull(voterId, "voterId");
        Objects.requireNonNull(teamOwnerId, "teamOwnerId");
    }
}
