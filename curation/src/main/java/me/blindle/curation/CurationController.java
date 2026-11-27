package me.blindle.curation;

import me.blindle.accounts.BlindleUser;
import me.blindle.content.ContentRegistry;
import me.blindle.content.Theme;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ProblemDetail;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Page admin des personnages (rôle admin, voir {@code SecurityConfig}). Chaque modification renvoie
 * le classement recalculé : {@code rarity} est la rareté au prochain démarrage, {@code liveRarity}
 * celle des parties en cours.
 */
@RestController
@RequestMapping("/api/admin/curation/universes")
public class CurationController {

    private final CurationService curation;
    private final ContentRegistry content;

    public CurationController(CurationService curation, ContentRegistry content) {
        this.curation = curation;
        this.content = content;
    }

    public record UniverseSummary(String slug, String name, String cover, String color, long characters) {
    }

    public record CharacterView(int malId, String name, String tier, Integer favourites, String role, String image,
                                int popularity, int power, double score, int rank, String rarity, String liveRarity,
                                String forcedRarity) {
    }

    public record UniverseDetail(String slug, String name, String note, int deckSize,
                                 Map<String, Long> rarities, List<CharacterView> characters) {
    }

    /** {@code rarity} : null = inchangée, "auto" = rendue au calcul, sinon rareté imposée. */
    public record Edit(String name, String tier, String rarity) {
    }

    public record Add(int malId, String name, String tier) {
    }

    @GetMapping
    public List<UniverseSummary> universes() {
        return curation.universes().stream()
                .map(u -> new UniverseSummary(u.slug(), u.name(), u.cover(), u.color(), curation.count(u.slug())))
                .toList();
    }

    @GetMapping("/{slug}")
    public UniverseDetail detail(@PathVariable String slug) {
        AnimeUniverse u = curation.universe(slug);
        Map<String, String> live = content.theme("anime-" + slug).map(Theme::cards).orElse(List.of()).stream()
                .collect(Collectors.toMap(Theme.CardDefinition::id, c -> c.rarity() == null ? "" : c.rarity(), (a, b) -> a));
        List<CharacterView> list = curation.ranking(slug).stream().map(r -> {
            RarityCalculator.Candidate c = r.candidate();
            String liveRarity = live.get("c" + c.malId());
            return new CharacterView(c.malId(), c.name(), c.tier(), c.favourites(), c.role(), c.image(),
                    r.popularity(), r.power(), Math.round(r.score() * 100) / 100.0, r.rank(), r.rarity(),
                    liveRarity == null || liveRarity.isEmpty() ? null : liveRarity, c.forcedRarity());
        }).toList();
        Map<String, Long> rarities = list.stream().filter(c -> c.rarity() != null)
                .collect(Collectors.groupingBy(CharacterView::rarity, Collectors.counting()));
        return new UniverseDetail(u.slug(), u.name(), u.note(), RarityCalculator.DECK_SIZE, rarities, list);
    }

    @PutMapping("/{slug}/characters/{malId}")
    public UniverseDetail edit(@PathVariable String slug, @PathVariable int malId, @RequestBody Edit body,
                               @AuthenticationPrincipal BlindleUser user) {
        if (body.name() != null || body.tier() != null) {
            curation.edit(slug, malId, body.name(), body.tier(), by(user));
        }
        if (body.rarity() != null) {
            curation.forceRarity(slug, malId, "auto".equals(body.rarity()) ? null : body.rarity(), by(user));
        }
        return detail(slug);
    }

    @DeleteMapping("/{slug}/characters/{malId}")
    public UniverseDetail remove(@PathVariable String slug, @PathVariable int malId) {
        curation.remove(slug, malId);
        return detail(slug);
    }

    @PostMapping("/{slug}/characters")
    public UniverseDetail add(@PathVariable String slug, @RequestBody Add body,
                              @AuthenticationPrincipal BlindleUser user) {
        curation.add(slug, body.malId(), body.name(), body.tier(), by(user));
        return detail(slug);
    }

    @GetMapping("/{slug}/search")
    public List<MalClient.MalCharacter> search(@PathVariable String slug, @RequestParam(defaultValue = "") String q) {
        return curation.search(slug, q);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail notFound(NoSuchElementException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** Tenrai injoignable (ajout, recherche). */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail upstream(IllegalStateException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    private static UUID by(BlindleUser user) {
        return user == null ? null : user.accountId();
    }
}
