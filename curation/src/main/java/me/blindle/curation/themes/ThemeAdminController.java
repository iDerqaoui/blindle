package me.blindle.curation.themes;

import com.fasterxml.jackson.databind.JsonNode;
import me.blindle.accounts.BlindleUser;
import me.blindle.content.ContentValidationException;
import me.blindle.content.Theme;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Page admin des cartes des thèmes écrits à la main (rôle admin, voir {@code SecurityConfig}).
 * Chaque modification renvoie le thème tel qu'il est désormais servi aux nouvelles rooms ; un thème
 * refusé par la validation renvoie 400 avec la liste des erreurs ({@code errors}).
 */
@RestController
@RequestMapping("/api/admin/content/themes")
public class ThemeAdminController {

    /** Valeurs permises par le schéma des thèmes (listes du formulaire). */
    static final List<String> RARITIES = List.of("common", "rare", "epic", "legendary");
    static final List<String> HINT_TYPES = List.of("text", "initial", "silhouette", "image-blur", "audio");

    private final ThemeEditor editor;

    public ThemeAdminController(ThemeEditor editor) {
        this.editor = editor;
    }

    public record ThemeSummary(String id, String name, int cards, boolean stored, Instant updatedAt) {
    }

    public record ThemeDetail(String id, String name, String description, Theme.Rights rights,
                              List<String> attributes, List<Theme.SlotDefinition> slots, List<Theme.CardDefinition> cards,
                              boolean stored, Instant updatedAt, List<String> presets,
                              List<String> rarities, List<String> hintTypes) {
    }

    @GetMapping
    public List<ThemeSummary> themes() {
        return editor.themes().stream().map(e -> new ThemeSummary(e.theme().id(), e.theme().name(),
                e.theme().cards().size(), e.stored(), e.updatedAt())).toList();
    }

    @GetMapping("/{id}")
    public ThemeDetail detail(@PathVariable String id) {
        return view(editor.theme(id));
    }

    @PostMapping("/{id}/cards")
    public ThemeDetail add(@PathVariable String id, @RequestBody JsonNode card, @AuthenticationPrincipal BlindleUser user) {
        return view(editor.putCard(id, null, card, by(user)));
    }

    @PutMapping("/{id}/cards/{cardId}")
    public ThemeDetail edit(@PathVariable String id, @PathVariable String cardId, @RequestBody JsonNode card,
                            @AuthenticationPrincipal BlindleUser user) {
        return view(editor.putCard(id, cardId, card, by(user)));
    }

    @DeleteMapping("/{id}/cards/{cardId}")
    public ThemeDetail remove(@PathVariable String id, @PathVariable String cardId,
                              @AuthenticationPrincipal BlindleUser user) {
        return view(editor.removeCard(id, cardId, by(user)));
    }

    /** Rend le fichier de content/themes (oublie la version en base). */
    @DeleteMapping("/{id}/stored")
    public ThemeDetail reset(@PathVariable String id) {
        return view(editor.reset(id));
    }

    private static ThemeDetail view(ThemeEditor.Edited e) {
        Theme t = e.theme();
        return new ThemeDetail(t.id(), t.name(), t.description(), t.rights(), t.attributes(), t.defaultSlots(),
                t.cards(), e.stored(), e.updatedAt(), e.presets(), RARITIES, HINT_TYPES);
    }

    @ExceptionHandler(ContentValidationException.class)
    public ProblemDetail invalid(ContentValidationException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                e.errors().size() == 1 ? e.errors().get(0) : e.errors().size() + " erreurs");
        p.setProperty("errors", e.errors());
        return p;
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail notFound(NoSuchElementException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    private static UUID by(BlindleUser user) {
        return user == null ? null : user.accountId();
    }
}
