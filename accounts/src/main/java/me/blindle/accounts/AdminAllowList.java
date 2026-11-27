package me.blindle.accounts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admins désignés par {@code blindle.admin.emails} (variable {@code BLINDLE_ADMIN_EMAILS}) :
 * liste séparée par des virgules d'e-mails (vérifiés par le fournisseur) ou d'identités
 * {@code fournisseur:identifiant} (ex. {@code discord:80351110224678912}). Vide : aucun admin.
 */
@Component
public class AdminAllowList {

    private final Set<String> entries;

    public AdminAllowList(@Value("${blindle.admin.emails:}") String raw) {
        this.entries = Arrays.stream(raw.split(","))
                .map(s -> s.strip().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(OAuthProfile p) {
        if (p.emailVerified() && p.email() != null && entries.contains(p.email().toLowerCase(Locale.ROOT))) {
            return true;
        }
        return entries.contains((p.provider() + ":" + p.subject()).toLowerCase(Locale.ROOT));
    }
}
