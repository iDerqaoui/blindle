package me.blindle.app;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.blindle.accounts.AccountService;
import me.blindle.accounts.BlindleUser;
import me.blindle.accounts.UserAccount;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Tests seulement (jamais dans l'image) : ouvre une session pour un compte de la base, comme le ferait
 * une connexion Google / Discord réussie (rôle lu en base). {@link #session} renvoie les en-têtes
 * (cookie de session + jeton CSRF) à joindre aux requêtes.
 */
@RestController
public class TestLogin {

    private final AccountService accounts;
    private final HttpSessionSecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

    public TestLogin(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/test/login")
    public String login(@RequestParam UUID account, HttpServletRequest request, HttpServletResponse response) {
        UserAccount a = accounts.find(account).orElseThrow();
        BlindleUser user = new BlindleUser(a.id(), "google", a.admin());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new OAuth2AuthenticationToken(user, user.getAuthorities(), "google"));
        contexts.saveContext(context, request, response);
        return "ok";
    }

    /** En-têtes d'une session connectée sur ce compte, avec cookie et en-tête CSRF. */
    public static HttpHeaders session(TestRestTemplate rest, UUID account) {
        List<String> cookies = rest.getForEntity("/api/me", String.class).getHeaders().get(HttpHeaders.SET_COOKIE);
        String xsrf = cookies.stream().filter(c -> c.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow()
                .split(";")[0].substring("XSRF-TOKEN=".length());
        ResponseEntity<String> login = rest.getForEntity("/test/login?account=" + account, String.class);
        String session = login.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("JSESSIONID=")).findFirst().orElseThrow().split(";")[0];
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + xsrf + "; " + session);
        h.add("X-XSRF-TOKEN", xsrf);
        return h;
    }
}
