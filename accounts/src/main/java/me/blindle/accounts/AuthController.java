package me.blindle.accounts;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Connexion côté front : {@code GET /api/auth/providers} liste les boutons à afficher, la connexion
 * elle-même est une redirection vers {@code /oauth2/authorization/<fournisseur>}, la déconnexion un
 * {@code POST /api/auth/logout}. {@code GET /api/me} : le compte connecté, ou 204 sans connexion.
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthProviders providers;
    private final AccountService accounts;

    public AuthController(AuthProviders providers, AccountService accounts) {
        this.providers = providers;
        this.accounts = accounts;
    }

    public record Me(String id, String displayName, String avatarUrl, String provider, boolean admin) {
    }

    public record Rename(String displayName) {
    }

    @GetMapping("/auth/providers")
    public List<String> providers() {
        return providers.ids();
    }

    @GetMapping("/me")
    public ResponseEntity<Me> me(@AuthenticationPrincipal BlindleUser user) {
        if (user == null) {
            return ResponseEntity.noContent().build();
        }
        return accounts.find(user.accountId())
                .map(a -> ResponseEntity.ok(view(a, user)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PatchMapping("/me")
    public Me rename(@AuthenticationPrincipal BlindleUser user, @RequestBody Rename body) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        try {
            return view(accounts.rename(user.accountId(), body.displayName()), user);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private static Me view(UserAccount a, BlindleUser user) {
        return new Me(a.id().toString(), a.displayName(), a.avatarUrl(), user.provider(), user.admin());
    }
}
