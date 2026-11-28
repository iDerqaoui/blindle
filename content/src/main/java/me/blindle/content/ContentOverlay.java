package me.blindle.content;

/**
 * Contenu gardé ailleurs que dans {@code content/} (en base) : chaque document remplace le fichier
 * de même identifiant, ou ajoute un thème / preset. Lu au démarrage et à chaque
 * {@link ContentRegistry#reload()}.
 */
public interface ContentOverlay {

    ContentLoader.Overrides overrides();
}
