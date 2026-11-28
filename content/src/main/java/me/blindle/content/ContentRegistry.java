package me.blindle.content;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Contenu servi aux nouvelles parties. Un contenu invalide au démarrage empêche l'application de
 * démarrer. Dossier : {@code blindle.content.dir}, sinon {@code content/} ou {@code ../content/}
 * (lancement depuis la racine ou depuis un module). Les {@link ContentGenerator} éventuels écrivent
 * leurs fichiers juste avant le premier chargement ; les {@link ContentOverlay} (contenu édité en base)
 * remplacent les fichiers de même identifiant.
 * <p>
 * {@link #reload()} recharge à chaud (sans relancer les générateurs) : le nouveau contenu ne remplace
 * l'ancien que s'il passe la validation et les {@link ContentCheck}. Les rooms déjà créées gardent le
 * contenu de leur création.
 */
@Component
public class ContentRegistry {

    private final Path dir;
    private final List<ContentGenerator> generators;
    private final Supplier<List<ContentOverlay>> overlays;
    private final Supplier<List<ContentCheck>> checks;
    private volatile ContentLoader.Content content;

    public ContentRegistry(String configuredDir) {
        this(configuredDir, List.of(), List::of, List::of);
    }

    @Autowired
    public ContentRegistry(@Value("${blindle.content.dir:}") String configuredDir,
                           ObjectProvider<ContentGenerator> generators,
                           ObjectProvider<ContentOverlay> overlays,
                           ObjectProvider<ContentCheck> checks) {
        this(configuredDir, generators.orderedStream().toList(),
                () -> overlays.orderedStream().toList(), () -> checks.orderedStream().toList());
    }

    private ContentRegistry(String configuredDir, List<ContentGenerator> generators,
                            Supplier<List<ContentOverlay>> overlays, Supplier<List<ContentCheck>> checks) {
        this.dir = resolveDir(configuredDir);
        this.generators = generators;
        this.overlays = overlays;
        this.checks = checks;
        generators.forEach(g -> g.generate(dir));
        // Les ContentCheck (moteur) dépendent de ce registre : ils valident le premier contenu eux-mêmes.
        this.content = initialLoad();
    }

    /**
     * Documents en base invalides (schéma changé depuis…) : on démarre sur les fichiers seuls plutôt
     * que de refuser de démarrer ; l'administrateur voit l'erreur en rechargeant depuis sa page.
     */
    private ContentLoader.Content initialLoad() {
        ContentLoader.Overrides stored = overrides(ContentLoader.Overrides.NONE);
        try {
            return new ContentLoader().load(dir, stored);
        } catch (ContentValidationException e) {
            if (stored.themes().isEmpty() && stored.presets().isEmpty()) {
                throw e;
            }
            org.apache.commons.logging.LogFactory.getLog(ContentRegistry.class)
                    .warn("Contenu en base ignoré au démarrage : " + e.getMessage());
            return new ContentLoader().load(dir);
        }
    }

    public static Path resolveDir(String configured) {
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        for (String candidate : List.of("content", "../content")) {
            Path p = Path.of(candidate);
            if (Files.isDirectory(p.resolve("themes"))) {
                return p;
            }
        }
        return Path.of("content");
    }

    /**
     * Contenu qu'on obtiendrait avec ces changements par-dessus les documents en base (une valeur
     * {@code NullNode} retire le document : le fichier revient). Lève {@link ContentValidationException}
     * avec toutes les erreurs, contrôles compris. Rien n'est servi ni enregistré.
     */
    public ContentLoader.Content preview(ContentLoader.Overrides changes) {
        ContentLoader.Content next = new ContentLoader().load(dir, overrides(changes));
        List<String> errors = new ArrayList<>();
        checks.get().forEach(c -> errors.addAll(c.check(next)));
        if (!errors.isEmpty()) {
            throw new ContentValidationException(errors);
        }
        return next;
    }

    /** Recharge fichiers et documents en base ; en cas d'erreur, le contenu servi ne change pas. */
    public synchronized void reload() {
        this.content = preview(ContentLoader.Overrides.NONE);
    }

    /** Thème produit par un générateur (édité à sa source, ex. la curation anime). */
    public boolean generated(String themeId) {
        return generators.stream().anyMatch(g -> g.generates(themeId));
    }

    private ContentLoader.Overrides overrides(ContentLoader.Overrides changes) {
        Map<String, JsonNode> themes = new LinkedHashMap<>();
        Map<String, JsonNode> presets = new LinkedHashMap<>();
        for (ContentOverlay overlay : overlays.get()) {
            ContentLoader.Overrides o = overlay.overrides();
            themes.putAll(o.themes());
            presets.putAll(o.presets());
        }
        apply(themes, changes.themes());
        apply(presets, changes.presets());
        return new ContentLoader.Overrides(themes, presets);
    }

    private static void apply(Map<String, JsonNode> target, Map<String, JsonNode> changes) {
        changes.forEach((id, node) -> {
            if (node == null || node.isNull()) {
                target.remove(id);
            } else {
                target.put(id, node);
            }
        });
    }

    public Path dir() {
        return dir;
    }

    public Optional<Theme> theme(String id) {
        return Optional.ofNullable(content.themes().get(id));
    }

    public Optional<Preset> preset(String id) {
        return Optional.ofNullable(content.presets().get(id));
    }

    public Collection<Theme> themes() {
        return content.themes().values();
    }

    public List<Preset> presets() {
        return content.presets().values().stream().sorted(Comparator.comparing(Preset::id)).toList();
    }
}
