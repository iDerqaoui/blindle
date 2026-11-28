package me.blindle.curation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import me.blindle.content.ContentGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Au démarrage, écrit {@code themes/anime-<univers>.json} et {@code presets/anime-<univers>.json}
 * depuis la curation en base (même format que {@code tools/import-anime.mjs}, qui reste utilisable
 * hors ligne). Désactivable par {@code blindle.curation.enabled=false}. Un univers en échec
 * (Tenrai injoignable au premier démarrage…) garde ses fichiers existants.
 */
@Component
public class AnimeThemeGenerator implements ContentGenerator {

    private static final Logger log = LoggerFactory.getLogger(AnimeThemeGenerator.class);
    /** En dessous, le paquet serait trop maigre pour une partie : on garde les fichiers existants. */
    static final int MIN_CARDS = 20;

    private final CurationService curation;
    private final boolean enabled;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public AnimeThemeGenerator(CurationService curation, @Value("${blindle.curation.enabled:true}") boolean enabled) {
        this.curation = curation;
        this.enabled = enabled;
    }

    @Override
    public void generate(Path contentDir) {
        if (!enabled) {
            return;
        }
        for (AnimeUniverse u : curation.universes()) {
            try {
                generate(contentDir, u.slug());
            } catch (RuntimeException e) {
                log.warn("Univers {} : thème non régénéré ({})", u.slug(), e.getMessage());
            }
        }
    }

    /** Les thèmes {@code anime-*} s'éditent par la curation des personnages (même désactivée). */
    @Override
    public boolean generates(String themeId) {
        return themeId.startsWith("anime-");
    }

    /** Régénère un univers, même si la génération au démarrage est désactivée. */
    public void generate(Path contentDir, String slug) {
        int seeded = curation.seedFromFile(slug, contentDir.resolve("curation").resolve("anime-" + slug + ".json"));
        if (seeded > 0) {
            log.info("Univers {} : {} personnages repris du fichier de curation", slug, seeded);
        }
        try {
            List<String> notFound = curation.fillMalData(slug);
            if (!notFound.isEmpty()) {
                log.warn("Univers {} : introuvables sur MyAnimeList, ignorés : {}", slug, notFound);
            }
        } catch (RuntimeException e) {
            log.warn("Univers {} : données MyAnimeList incomplètes ({}), thème construit avec l'existant",
                    slug, e.getMessage());
        }
        AnimeUniverse u = curation.universe(slug);
        List<RarityCalculator.Ranked> deck = curation.ranking(slug).stream()
                .filter(RarityCalculator.Ranked::inDeck).toList();
        if (deck.size() < MIN_CARDS) {
            log.warn("Univers {} : {} cartes seulement, fichiers existants conservés", slug, deck.size());
            return;
        }
        String id = "anime-" + slug;
        write(contentDir.resolve("themes").resolve(id + ".json"), theme(id, u, deck));
        write(contentDir.resolve("presets").resolve(id + ".json"), preset(id, u, deck.size()));
        log.info("Univers {} : {} cartes", slug, deck.size());
    }

    static Map<String, Object> theme(String id, AnimeUniverse u, List<RarityCalculator.Ranked> deck) {
        Map<String, Object> rights = new LinkedHashMap<>();
        rights.put("profile", "private");
        rights.put("channels", List.of("web"));
        rights.put("visibility", "private");
        rights.put("imagePolicy", "any");
        rights.put("disclaimer", "Characters and artwork © their respective owners, via MyAnimeList (Tenrai API). "
                + "Private play among friends, not affiliated.");
        rights.put("takedownContact", "contact@blindle.me");

        Map<String, Object> art = new LinkedHashMap<>();
        art.put("cover", u.cover());
        art.put("banner", null);
        art.put("color", u.color());

        Map<String, Object> t = new LinkedHashMap<>();
        t.put("id", id);
        t.put("name", u.name());
        t.put("version", "2.0.0");
        t.put("description", deck.size() + " characters from " + (u.title() != null ? u.title() : u.name())
                + ", ranked by popularity (MyAnimeList) and power.");
        t.put("rights", rights);
        t.put("art", art);
        t.put("defaultSlots", IntStream.rangeClosed(1, 5)
                .mapToObj(i -> Map.of("id", "slot-" + i, "label", "Carte " + i)).toList());
        t.put("attributes", List.of("popularity", "power"));
        t.put("cards", deck.stream().map(AnimeThemeGenerator::card).toList());
        return t;
    }

    private static Map<String, Object> card(RarityCalculator.Ranked r) {
        RarityCalculator.Candidate c = r.candidate();
        boolean main = "Main".equals(c.role());
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", "c" + c.malId());
        card.put("name", c.name());
        card.put("tags", List.of(main ? "principal" : "secondaire", "tier-" + c.tier().toLowerCase()));
        card.put("rarity", r.rarity());
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("popularity", r.popularity());
        stats.put("power", r.power());
        card.put("stats", stats);
        card.put("image", c.image());
        card.put("hints", List.of(
                Map.of("level", 1, "type", "text", "text", main ? "Main character" : "Supporting character"),
                Map.of("level", 2, "type", "initial", "value", c.name().substring(0, 1).toUpperCase())));
        return card;
    }

    /**
     * Mode principal « Anime Blind Bid » : le piocheur voit la carte et la vend, les autres
     * surenchérissent à l'aveugle ; verdict aux points, défausse payante.
     */
    static Map<String, Object> preset(String id, AnimeUniverse u, int cards) {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("budget", 1000);
        settings.put("rounds", cards);
        settings.put("teamSize", 5);
        settings.put("hintLevels", 1);
        settings.put("bidTimerSec", 20);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", id);
        p.put("name", u.name());
        p.put("description", "Anime Blind Bid: the drawer secretly sees the character and opens the bidding; the "
                + "others outbid blind. Buying costs gold, passing is free, but the drawer pays 150 gold to let a "
                + "card go to the trash. Score: rarity points − empty slots (unspent gold is worth nothing).");
        p.put("theme", id);
        p.put("bidMode", "drawer");
        p.put("verdictMode", "points");
        p.put("settings", settings);
        p.put("rules", List.of(
                Map.of("ref", "budget-cap"),
                Map.of("ref", "anti-snipe", "params", Map.of("extendSeconds", 5)),
                Map.of("ref", "discard-fee", "params", Map.of("amount", 150))));
        return p;
    }

    private void write(Path file, Map<String, Object> json) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, mapper.writeValueAsString(json) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Écriture impossible de " + file + " : " + e.getMessage(), e);
        }
    }
}
