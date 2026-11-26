package me.blindle.core.model;

import java.util.Objects;

/**
 * Indice d'une carte. {@code type} : text, initial, silhouette, image-blur, audio…
 * {@code value} : texte ou valeur selon le type. Le moteur ne l'interprète pas.
 */
public record Hint(int level, String type, String value) {

    public Hint {
        if (level < 1) {
            throw new IllegalArgumentException("level >= 1 requis");
        }
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
    }
}
