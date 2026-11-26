package me.blindle.core.model;

import java.util.Objects;

/** Résultat d'un palier d'enchère : qui gagne et combien il paie. */
public record LotResult(String winnerId, int price) {

    public LotResult {
        Objects.requireNonNull(winnerId, "winnerId");
        if (price < 0) {
            throw new IllegalArgumentException("price >= 0");
        }
    }
}
