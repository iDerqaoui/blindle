package me.blindle.accounts;

import java.util.UUID;

/**
 * Un joueur a supprimé son compte : chaque module efface ce qu'il garde sur lui (résultats du défi
 * du jour…). Publié dans la transaction de suppression.
 */
public record AccountDeletedEvent(UUID accountId) {
}
