package me.blindle.content;

import java.util.Arrays;
import java.util.Optional;

/**
 * Canal de diffusion (§8). Toujours déduit côté serveur de l'authentification du joueur,
 * jamais d'un paramètre envoyé par le client.
 */
public enum Channel {
    WEB("web"),
    DISCORD("discord");

    private final String id;

    Channel(String id) {
        this.id = id;
    }

    /** Identifiant utilisé dans les fichiers de contenu ({@code web}, {@code discord}). */
    public String id() {
        return id;
    }

    public static Optional<Channel> of(String id) {
        return Arrays.stream(values()).filter(c -> c.id.equals(id)).findFirst();
    }
}
