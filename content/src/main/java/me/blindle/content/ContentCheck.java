package me.blindle.content;

import java.util.List;

/**
 * Contrôle supplémentaire d'un contenu candidat, au-delà du schéma (ex. règles et modes connus du
 * moteur). Appelé avant de remplacer le contenu servi : un contenu refusé n'est jamais servi.
 */
public interface ContentCheck {

    /** Erreurs, vides si le contenu est acceptable. */
    List<String> check(ContentLoader.Content content);
}
