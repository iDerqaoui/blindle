package me.blindle.app;

import me.blindle.accounts.AccountDeletedEvent;
import me.blindle.realtime.solo.who.WhoResultRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Compte supprimé : efface ce que les autres modules gardent sur lui. Ici, les résultats du défi
 * Who's That? (clé {@code u:<compte>}, voir {@code WhoController}). Même transaction que la suppression.
 */
@Component
public class AccountCleanup {

    private final WhoResultRepository whoResults;

    public AccountCleanup(WhoResultRepository whoResults) {
        this.whoResults = whoResults;
    }

    @EventListener
    public void onDeleted(AccountDeletedEvent event) {
        whoResults.deleteByPlayerKey("u:" + event.accountId());
    }
}
