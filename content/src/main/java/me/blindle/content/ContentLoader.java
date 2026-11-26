package me.blindle.content;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Charge et valide le contenu d'un dossier : {@code themes/*.json} et {@code presets/*.json}.
 * Toutes les erreurs sont collectées puis levées ensemble ({@link ContentValidationException}).
 */
public final class ContentLoader {

    /** Contenu chargé, indexé par identifiant. */
    public record Content(Map<String, Theme> themes, Map<String, Preset> presets) {
        public Content {
            themes = Map.copyOf(themes);
            presets = Map.copyOf(presets);
        }
    }

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final SchemaValidator schemas = new SchemaValidator();

    public Content load(Path dir) {
        List<String> errors = new ArrayList<>();
        Map<String, Theme> themes = new LinkedHashMap<>();
        Map<String, Preset> presets = new LinkedHashMap<>();

        for (Path file : jsonFiles(dir.resolve("themes"), errors)) {
            Theme theme = parse(file, Theme.class, true, errors);
            if (theme == null) {
                continue;
            }
            if (themes.containsKey(theme.id())) {
                errors.add(file.getFileName() + " : thème « " + theme.id() + " » déjà défini");
                continue;
            }
            checkTheme(file.getFileName().toString(), theme, errors);
            themes.put(theme.id(), theme);
        }
        for (Path file : jsonFiles(dir.resolve("presets"), errors)) {
            Preset preset = parse(file, Preset.class, false, errors);
            if (preset == null) {
                continue;
            }
            if (presets.containsKey(preset.id())) {
                errors.add(file.getFileName() + " : preset « " + preset.id() + " » déjà défini");
                continue;
            }
            checkPreset(file.getFileName().toString(), preset, themes, errors);
            presets.put(preset.id(), preset);
        }
        if (!errors.isEmpty()) {
            throw new ContentValidationException(errors);
        }
        return new Content(themes, presets);
    }

    private List<Path> jsonFiles(Path dir, List<String> errors) {
        if (!Files.isDirectory(dir)) {
            errors.add("Dossier introuvable : " + dir.toAbsolutePath());
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            errors.add("Lecture impossible de " + dir + " : " + e.getMessage());
            return List.of();
        }
    }

    private <T> T parse(Path file, Class<T> type, boolean theme, List<String> errors) {
        String name = file.getFileName().toString();
        try {
            JsonNode node = mapper.readTree(file.toFile());
            List<String> schemaErrors = theme ? schemas.validateTheme(node) : schemas.validatePreset(node);
            if (!schemaErrors.isEmpty()) {
                schemaErrors.forEach(e -> errors.add(name + " : " + e));
                return null;
            }
            return mapper.treeToValue(node, type);
        } catch (IOException e) {
            String detail = e instanceof com.fasterxml.jackson.core.JsonProcessingException jpe
                    ? jpe.getOriginalMessage() : e.getMessage();
            errors.add(name + " : JSON illisible (" + detail + ")");
            return null;
        } catch (RuntimeException e) {
            errors.add(name + " : " + e.getMessage());
            return null;
        }
    }

    /** Vérifications que le schéma ne peut pas exprimer. */
    private void checkTheme(String file, Theme theme, List<String> errors) {
        Set<String> slotIds = new HashSet<>();
        theme.defaultSlots().forEach(s -> {
            if (!slotIds.add(s.id())) {
                errors.add(file + " : slot « " + s.id() + " » en double");
            }
        });
        Set<String> cardIds = new HashSet<>();
        for (Theme.CardDefinition card : theme.cards()) {
            if (!cardIds.add(card.id())) {
                errors.add(file + " : carte « " + card.id() + " » en double");
            }
            Set<Integer> levels = new HashSet<>();
            for (Theme.HintDefinition hint : card.hints()) {
                if (!levels.add(hint.level())) {
                    errors.add(file + " : carte « " + card.id() + " », palier d'indice " + hint.level() + " en double");
                }
            }
            if (card.stats() != null) {
                card.stats().keySet().stream()
                        .filter(k -> !theme.attributes().contains(k))
                        .forEach(k -> errors.add(file + " : carte « " + card.id() + " », attribut « " + k
                                + " » absent de « attributes »"));
            }
            if (card.image() != null && "none".equals(theme.rights().imagePolicy())) {
                errors.add(file + " : carte « " + card.id() + " » a une image alors que imagePolicy vaut « none »");
            }
        }
        RightsPolicy.check(theme.rights()).forEach(e -> errors.add(file + " : " + e));
    }

    private void checkPreset(String file, Preset preset, Map<String, Theme> themes, List<String> errors) {
        Theme theme = themes.get(preset.theme());
        if (theme == null) {
            errors.add(file + " : thème « " + preset.theme() + " » introuvable");
            return;
        }
        Preset.Settings s = preset.settings();
        if (s.teamSize() > theme.defaultSlots().size()) {
            errors.add(file + " : teamSize " + s.teamSize() + " > " + theme.defaultSlots().size() + " slots du thème");
        }
        if (s.rounds() > theme.cards().size()) {
            errors.add(file + " : " + s.rounds() + " rounds pour seulement " + theme.cards().size() + " cartes");
        }
        int minHints = theme.cards().stream().mapToInt(c -> c.hints().size()).min().orElse(0);
        if (s.hintLevels() > minHints) {
            errors.add(file + " : hintLevels " + s.hintLevels() + " > indices disponibles (" + minHints + ")");
        }
        for (String channel : preset.channels()) {
            if (!theme.rights().channels().contains(channel)) {
                errors.add(file + " : canal « " + channel + " » non autorisé par le thème");
            }
        }
        Map<String, Integer> refs = new HashMap<>();
        preset.rules().forEach(r -> refs.merge(r.ref(), 1, Integer::sum));
        refs.forEach((ref, n) -> {
            if (n > 1) {
                errors.add(file + " : règle « " + ref + " » déclarée " + n + " fois");
            }
        });
    }
}
