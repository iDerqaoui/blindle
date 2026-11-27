package me.blindle.content;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Contenu chargé au démarrage. Un contenu invalide empêche l'application de démarrer.
 * Dossier : {@code blindle.content.dir}, sinon {@code content/} ou {@code ../content/}
 * (lancement depuis la racine ou depuis un module). Les {@link ContentGenerator} éventuels
 * écrivent leurs fichiers juste avant le chargement.
 */
@Component
public class ContentRegistry {

    private final ContentLoader.Content content;
    private final Path dir;

    public ContentRegistry(String configuredDir) {
        this(configuredDir, List.of());
    }

    @Autowired
    public ContentRegistry(@Value("${blindle.content.dir:}") String configuredDir,
                           ObjectProvider<ContentGenerator> generators) {
        this(configuredDir, generators.orderedStream().toList());
    }

    private ContentRegistry(String configuredDir, List<ContentGenerator> generators) {
        this.dir = resolveDir(configuredDir);
        generators.forEach(g -> g.generate(dir));
        this.content = new ContentLoader().load(dir);
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
