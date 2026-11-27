package me.blindle.curation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Liste de curation des univers anime, en base : personnages retenus, nom d'affichage, rang de
 * puissance. La première fois, elle est reprise de {@code content/curation/anime-<univers>.json}.
 * Les modifications s'appliquent aux parties au prochain démarrage ({@link AnimeThemeGenerator}) ;
 * {@link #ranking} donne dès maintenant la rareté qu'elles produiront.
 */
@Service
public class CurationService {

    public static final int MAX_NAME = 100;
    private static final int SEARCH_LIMIT = 20;

    private final AnimeUniverseRepository universes;
    private final AnimeCharacterRepository characters;
    private final MalClient mal;
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper();
    /** Distribution MyAnimeList par univers, gardée le temps de vie du serveur (recherches admin). */
    private final Map<String, List<MalClient.MalCharacter>> casts = new ConcurrentHashMap<>();

    public CurationService(AnimeUniverseRepository universes, AnimeCharacterRepository characters,
                           MalClient mal, Clock clock) {
        this.universes = universes;
        this.characters = characters;
        this.mal = mal;
        this.clock = clock;
    }

    public List<AnimeUniverse> universes() {
        return universes.findAllByOrderByPosition();
    }

    public AnimeUniverse universe(String slug) {
        return universes.findById(slug).orElseThrow(() -> new NoSuchElementException("Univers inconnu : " + slug));
    }

    public List<AnimeCharacter> characters(String slug) {
        return characters.findByUniverseSlugOrderByPosition(slug);
    }

    public long count(String slug) {
        return characters.countByUniverseSlug(slug);
    }

    /** Classement et rareté que produirait la liste actuelle. */
    public List<RarityCalculator.Ranked> ranking(String slug) {
        return RarityCalculator.rank(characters(slug).stream().map(AnimeCharacter::candidate).toList());
    }

    /** Reprend le fichier de curation d'un univers encore vide en base. Renvoie le nombre de personnages repris. */
    @Transactional
    public int seedFromFile(String slug, Path file) {
        AnimeUniverse u = universe(slug);
        if (characters.countByUniverseSlug(slug) > 0 || !Files.isRegularFile(file)) {
            return 0;
        }
        JsonNode root;
        try {
            root = mapper.readTree(file.toFile());
        } catch (IOException e) {
            throw new IllegalStateException(file + " illisible : " + e.getMessage(), e);
        }
        if (root.hasNonNull("note")) {
            u.note(root.get("note").asText());
        }
        List<AnimeCharacter> list = new ArrayList<>();
        Set<Integer> seen = new java.util.HashSet<>();
        for (JsonNode c : root.path("characters")) {
            int malId = c.get(0).asInt();
            String tier = c.get(2).asText();
            RarityCalculator.power(tier);
            if (seen.add(malId)) {
                list.add(new AnimeCharacter(slug, malId, c.get(1).asText(), tier, list.size(), clock.instant(), null));
            }
        }
        characters.saveAll(list);
        return list.size();
    }

    /**
     * Complète depuis MyAnimeList les personnages sans données (favoris, image, rôle) et
     * l'affiche de l'univers. Appelle le réseau seulement s'il manque quelque chose.
     * Renvoie les noms des personnages introuvables dans la distribution.
     */
    public List<String> fillMalData(String slug) {
        AnimeUniverse u = universe(slug);
        List<AnimeCharacter> missing = characters(slug).stream().filter(c -> !c.hasMalData()).toList();
        List<String> notFound = new ArrayList<>();
        if (!missing.isEmpty()) {
            Map<Integer, MalClient.MalCharacter> byId = cast(u).stream()
                    .collect(Collectors.toMap(MalClient.MalCharacter::malId, Function.identity(), (a, b) -> a));
            for (AnimeCharacter c : missing) {
                MalClient.MalCharacter m = byId.get(c.malId());
                if (m == null) {
                    notFound.add(c.name());
                } else {
                    c.malData(m);
                }
            }
            characters.saveAll(missing);
        }
        if (u.title() == null) {
            mal.anime(u.malIds().get(0)).ifPresent(a -> {
                u.anime(a.title(), a.cover());
                universes.save(u);
            });
        }
        return notFound;
    }

    @Transactional
    public void edit(String slug, int malId, String name, String tier, UUID by) {
        AnimeCharacter c = find(slug, malId);
        c.edit(name == null ? null : checkName(name), tier == null ? null : checkTier(tier), clock.instant(), by);
    }

    /** Impose une rareté (ou la rend au calcul avec null). */
    @Transactional
    public void forceRarity(String slug, int malId, String rarity, UUID by) {
        find(slug, malId).forceRarity(RarityCalculator.checkRarity(rarity), clock.instant(), by);
    }

    @Transactional
    public void remove(String slug, int malId) {
        characters.delete(find(slug, malId));
    }

    /** Ajoute un personnage de la distribution MyAnimeList de l'univers (nom par défaut : celui de MAL, remis dans l'ordre). */
    @Transactional
    public void add(String slug, int malId, String name, String tier, UUID by) {
        AnimeUniverse u = universe(slug);
        if (characters.existsById(new AnimeCharacter.Key(slug, malId))) {
            throw new IllegalArgumentException("Personnage déjà retenu : " + malId);
        }
        MalClient.MalCharacter m = cast(u).stream().filter(c -> c.malId() == malId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Personnage " + malId + " absent de " + u.name()));
        int position = characters(slug).stream().mapToInt(AnimeCharacter::position).max().orElse(-1) + 1;
        String display = name == null || name.isBlank() ? displayName(m.name()) : name;
        AnimeCharacter c = new AnimeCharacter(slug, malId, checkName(display), checkTier(tier), position,
                clock.instant(), by);
        c.malData(m);
        characters.save(c);
    }

    /** Personnages de la distribution, pas encore retenus, dont le nom contient la recherche. */
    public List<MalClient.MalCharacter> search(String slug, String query) {
        AnimeUniverse u = universe(slug);
        Set<Integer> kept = characters(slug).stream().map(AnimeCharacter::malId).collect(Collectors.toSet());
        String q = normalize(query);
        return cast(u).stream()
                .filter(c -> !kept.contains(c.malId()))
                .filter(c -> q.isEmpty() || normalize(c.name()).contains(q) || normalize(displayName(c.name())).contains(q))
                .sorted(Comparator.comparingInt(MalClient.MalCharacter::favourites).reversed())
                .limit(SEARCH_LIMIT)
                .map(c -> new MalClient.MalCharacter(c.malId(), displayName(c.name()), c.favourites(), c.role(), c.image()))
                .toList();
    }

    /** « Uzumaki, Naruto » → « Naruto Uzumaki ». */
    public static String displayName(String malName) {
        String[] parts = malName.split(", ");
        return parts.length == 2 ? parts[1].strip() + " " + parts[0].strip() : malName.strip();
    }

    private List<MalClient.MalCharacter> cast(AnimeUniverse u) {
        return casts.computeIfAbsent(u.slug(), s -> mal.cast(u.malIds()));
    }

    private AnimeCharacter find(String slug, int malId) {
        return characters.findById(new AnimeCharacter.Key(slug, malId))
                .orElseThrow(() -> new NoSuchElementException("Personnage inconnu : " + malId));
    }

    private static String checkTier(String tier) {
        String t = tier.strip().toUpperCase(Locale.ROOT);
        RarityCalculator.power(t);
        return t;
    }

    private static String checkName(String name) {
        String n = name.strip();
        if (n.isEmpty() || n.length() > MAX_NAME) {
            throw new IllegalArgumentException("Nom de 1 à " + MAX_NAME + " caractères");
        }
        return n;
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replace(",", "").strip();
    }
}
