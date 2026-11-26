package me.blindle.content;

import java.util.List;

/** Contenu invalide : liste toutes les erreurs trouvées, fichier et chemin compris. */
public class ContentValidationException extends RuntimeException {

    private final List<String> errors;

    public ContentValidationException(List<String> errors) {
        super("Contenu invalide (" + errors.size() + " erreur(s)) :\n  - " + String.join("\n  - ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
