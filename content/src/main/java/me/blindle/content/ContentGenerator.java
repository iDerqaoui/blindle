package me.blindle.content;

import java.nio.file.Path;

/**
 * Produit des fichiers de contenu ({@code themes/*.json}, {@code presets/*.json}) juste avant leur
 * chargement au démarrage. Le moteur n'en sait rien : le contenu généré passe par la même validation
 * que le contenu écrit à la main. Une erreur de génération ne doit pas empêcher le démarrage : le
 * générateur la journalise et laisse les fichiers existants en place.
 */
public interface ContentGenerator {

    void generate(Path contentDir);

    /** Ce thème est-il produit par ce générateur ? (il s'édite alors à sa source, pas comme un thème écrit à la main) */
    default boolean generates(String themeId) {
        return false;
    }
}
