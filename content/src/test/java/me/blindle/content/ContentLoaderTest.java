package me.blindle.content;

import me.blindle.core.model.Card;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentLoaderTest {

    private final ContentLoader loader = new ContentLoader();

    @Test
    void repositoryContentIsValid() {
        ContentLoader.Content content = loader.load(ContentRegistry.resolveDir(""));

        Theme manga = content.themes().get("manga-shonen");
        assertThat(manga.cards()).hasSize(30);
        assertThat(manga.rights().imagePolicy()).isEqualTo("none");
        assertThat(manga.rights().visibility()).isEqualTo("private");
        List<Card> cards = manga.toCards();
        assertThat(cards).allMatch(c -> c.hints().size() == 3 && c.hints().stream()
                .allMatch(h -> h.type().equals("text")));
        assertThat(manga.toSlots()).extracting(s -> s.id())
                .containsExactly("captain", "right", "tactician", "support", "joker");
        assertThat(manga.allCardsHave("stats")).isTrue();

        Preset preset = content.presets().get("manga-classique");
        assertThat(preset.theme()).isEqualTo("manga-shonen");
        assertThat(preset.settings().toGameSettings().budget()).isEqualTo(1000);
        assertThat(preset.rules()).extracting(Preset.RuleRef::ref).containsExactly("budget-cap", "rising-floor");
    }

    @Test
    void schemaErrorsAreReportedWithFileName(@TempDir Path dir) throws IOException {
        write(dir, "themes/bad.json", """
                { "id": "Bad Id", "name": "x", "defaultSlots": [], "cards": [] }
                """);
        Files.createDirectories(dir.resolve("presets"));

        assertThatThrownBy(() -> loader.load(dir))
                .isInstanceOf(ContentValidationException.class)
                .satisfies(e -> assertThat(((ContentValidationException) e).errors())
                        .isNotEmpty()
                        .allMatch(msg -> msg.startsWith("bad.json")));
    }

    @Test
    void semanticErrorsAreCollected(@TempDir Path dir) throws IOException {
        write(dir, "themes/t.json", """
                {
                  "id": "t", "name": "T",
                  "rights": { "profile": "real-people", "channels": ["web"], "visibility": "public",
                              "imagePolicy": "any" },
                  "defaultSlots": [ { "id": "a", "label": "A" } ],
                  "attributes": ["power"],
                  "cards": [
                    { "id": "c1", "name": "C1", "stats": { "speed": 3 },
                      "hints": [ { "level": 1, "type": "text", "text": "x" },
                                 { "level": 1, "type": "text", "text": "y" } ] },
                    { "id": "c1", "name": "Doublon",
                      "hints": [ { "level": 1, "type": "text", "text": "x" },
                                 { "level": 2, "type": "initial", "value": "D" } ] }
                  ]
                }
                """);
        write(dir, "presets/p.json", """
                { "id": "p", "name": "P", "theme": "missing", "bidMode": "sealed", "verdictMode": "vote",
                  "settings": { "budget": 10, "rounds": 1, "teamSize": 1, "hintLevels": 2, "bidTimerSec": 30 } }
                """);

        assertThatThrownBy(() -> loader.load(dir))
                .isInstanceOfSatisfying(ContentValidationException.class, e -> assertThat(e.errors())
                        .anyMatch(m -> m.contains("« c1 » en double"))
                        .anyMatch(m -> m.contains("palier d'indice 1 en double"))
                        .anyMatch(m -> m.contains("« speed »"))
                        .anyMatch(m -> m.contains("imagePolicy « any » interdit"))
                        .anyMatch(m -> m.contains("thème « missing » introuvable")));
    }

    @Test
    void hintMustHaveTextOrValue(@TempDir Path dir) throws IOException {
        write(dir, "themes/t.json", """
                { "id": "t", "name": "T",
                  "rights": { "profile": "free", "channels": ["web"], "visibility": "private", "imagePolicy": "none" },
                  "defaultSlots": [ { "id": "a", "label": "A" } ],
                  "cards": [ { "id": "c", "name": "C",
                    "hints": [ { "level": 1, "type": "text" }, { "level": 2, "type": "text", "text": "ok" } ] } ] }
                """);
        Files.createDirectories(dir.resolve("presets"));

        assertThatThrownBy(() -> loader.load(dir)).isInstanceOf(ContentValidationException.class);
    }

    @Test
    void missingFoldersAreExplicit(@TempDir Path dir) {
        assertThatThrownBy(() -> loader.load(dir))
                .hasMessageContaining("Dossier introuvable");
    }

    private static void write(Path dir, String file, String json) throws IOException {
        Path p = dir.resolve(file);
        Files.createDirectories(p.getParent());
        Files.writeString(p, json);
    }
}
