package me.blindle.accounts;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Connexion admin par mot de passe unique ({@code blindle.admin.password}, variable
 * {@code BLINDLE_ADMIN_PASSWORD}) : accès à la page admin sans configurer Google ni Discord.
 * Sans mot de passe configuré, la route répond 404. Le compte « Admin » est un compte comme un autre
 * (identité {@code local:admin}).
 */
@RestController
public class AdminPasswordController {

    private static final long FAILURE_DELAY_MS = 1000;

    private final byte[] password;
    private final AccountService accounts;
    private final SecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

    public AdminPasswordController(@Value("${blindle.admin.password:}") String password, AccountService accounts) {
        this.password = password.getBytes(StandardCharsets.UTF_8);
        this.accounts = accounts;
    }

    public record Login(String password) {
    }

    @PostMapping("/api/auth/admin-password")
    public ResponseEntity<Void> login(@RequestBody Login body, HttpServletRequest request,
                                      HttpServletResponse response) throws InterruptedException {
        if (password.length == 0) {
            return ResponseEntity.notFound().build();
        }
        String given = body.password() == null ? "" : body.password();
        if (!MessageDigest.isEqual(password, given.getBytes(StandardCharsets.UTF_8))) {
            Thread.sleep(FAILURE_DELAY_MS);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserAccount account = accounts.signIn(new OAuthProfile("local", "admin", null, false, "Admin", null));
        BlindleUser user = new BlindleUser(account.id(), "local", true);

        request.getSession(true);
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return ResponseEntity.noContent().build();
    }
}
