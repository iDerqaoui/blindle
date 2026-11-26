package me.blindle.core.model;

import java.util.Objects;

/** Offre d'un joueur sur le palier courant du lot. */
public record Bid(String playerId, int amount) {

    public Bid {
        Objects.requireNonNull(playerId, "playerId");
    }
}
