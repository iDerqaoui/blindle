package me.blindle.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.blindle.content.ContentRegistry;
import me.blindle.content.ContentValidationException;
import me.blindle.content.Theme;
import me.blindle.curation.themes.ThemeDocumentRepository;
import me.blindle.curation.themes.ThemeEditor;
import me.blindle.realtime.game.GameFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cartes des thèmes écrits à la main éditées en base : validées comme un fichier, servies aussitôt
 * aux nouvelles rooms, et le fichier revient quand on oublie la version en base.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ThemeAdminIntegrationTest {

    private static final String THEME = "manga-shonen";
    private static final String API = "/api/admin/content/themes";

    @Autowired
    TestRestTemplate rest;
    @Autowired
    ThemeEditor editor;
    @Autowired
    ContentRegistry content;
    @Autowired
    GameFactory factory;
    @Autowired
    ThemeDocumentRepository documents;
    @Autowired
    me.blindle.accounts.UserAccountRepository accounts;

    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach
    void clean() {
        editor.reset(THEME);
    }

    private Theme.CardDefinition card(String id) {
        return content.theme(THEME).orElseThrow().cards().stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
    }

    private JsonNode luffy(String name, int hints) {
        StringBuilder h = new StringBuilder();
        for (int i = 1; i <= hints; i++) {
            h.append(i > 1 ? "," : "").append("{\"level\":").append(i).append(",\"type\":\"text\",\"text\":\"Indice ")
                    .append(i).append("\",\"value\":null}");
        }
        try {
            return mapper.readTree("{\"id\":\"op-luffy\",\"name\":\"" + name + "\",\"tags\":[\"one-piece\",\"captain\"],"
                    + "\"rarity\":\"legendary\",\"isTrap\":null,\"image\":null,"
                    + "\"stats\":{\"power\":95,\"intelligence\":40,\"popularity\":99},\"hints\":[" + h + "]}");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void editedCardIsServedToNewRoomsAtOnceAndTheFileComesBackOnReset() {
        assertThat(editor.themes()).extracting(e -> e.theme().id())
                .contains(THEME, "pays-du-monde").noneMatch(id -> id.startsWith("anime-"));

        ThemeEditor.Edited edited = editor.putCard(THEME, "op-luffy", luffy("Luffy (édité)", 3), null);

        assertThat(edited.stored()).isTrue();
        assertThat(edited.presets()).contains("manga-classique");
        assertThat(card("op-luffy").name()).isEqualTo("Luffy (édité)");
        assertThat(card("op-luffy").stats()).containsEntry("power", 95);
        assertThat(factory.setup("manga-classique").deck()).anyMatch(c -> c.name().equals("Luffy (édité)"));
        assertThat(documents.existsById(THEME)).isTrue();

        // les autres cartes ne bougent pas, l'ordre est conservé
        assertThat(content.theme(THEME).orElseThrow().cards()).hasSize(33);
        assertThat(card("op-zoro")).isNotNull();

        editor.reset(THEME);
        assertThat(card("op-luffy").name()).isEqualTo("Monkey D. Luffy");
        assertThat(documents.existsById(THEME)).isFalse();
    }

    @Test
    void cardsCanBeAddedRenamedAndRemoved() {
        JsonNode newcomer = luffy("Nouveau", 3);
        ((com.fasterxml.jackson.databind.node.ObjectNode) newcomer).put("id", "new-hero");
        editor.putCard(THEME, null, newcomer, null);
        assertThat(content.theme(THEME).orElseThrow().cards()).hasSize(34);

        assertThatThrownBy(() -> editor.putCard(THEME, null, newcomer, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("existe déjà");

        ((com.fasterxml.jackson.databind.node.ObjectNode) newcomer).put("id", "renamed-hero");
        editor.putCard(THEME, "new-hero", newcomer, null);
        assertThat(card("new-hero")).isNull();
        assertThat(card("renamed-hero")).isNotNull();

        editor.removeCard(THEME, "renamed-hero", null);
        assertThat(content.theme(THEME).orElseThrow().cards()).hasSize(33);
    }

    @Test
    void invalidThemeIsRefusedAndNothingIsStored() {
        // 2 indices seulement : manga-classique en joue 3
        assertThatThrownBy(() -> editor.putCard(THEME, "op-luffy", luffy("Luffy", 2), null))
                .isInstanceOfSatisfying(ContentValidationException.class, e -> assertThat(e.errors())
                        .anyMatch(m -> m.contains("hintLevels 3")));
        // image alors que le thème n'en autorise pas
        JsonNode withImage = luffy("Luffy", 3);
        ((com.fasterxml.jackson.databind.node.ObjectNode) withImage).put("image", "https://img.test/luffy.png");
        assertThatThrownBy(() -> editor.putCard(THEME, "op-luffy", withImage, null))
                .isInstanceOfSatisfying(ContentValidationException.class, e -> assertThat(e.errors())
                        .anyMatch(m -> m.contains("imagePolicy")));

        assertThat(documents.existsById(THEME)).isFalse();
        assertThat(card("op-luffy").name()).isEqualTo("Monkey D. Luffy");
    }

    @Test
    void generatedThemesAreNotEditableHere() {
        content.themes().stream().map(Theme::id).filter(id -> id.startsWith("anime-")).findFirst()
                .ifPresent(id -> assertThatThrownBy(() -> editor.theme(id))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("généré"));
    }

    @Test
    void apiNeedsAnAdminSessionAndReportsValidationErrors() {
        assertThat(rest.getForEntity(API, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders h = adminSession();
        ResponseEntity<List> list = rest.exchange(API, HttpMethod.GET, new HttpEntity<>(h), List.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> refused = rest.exchange(API + "/" + THEME + "/cards/op-luffy", HttpMethod.PUT,
                new HttpEntity<>(luffy("Luffy", 2).toString(), h), Map.class);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((List<String>) refused.getBody().get("errors")).anyMatch(m -> m.contains("hintLevels"));

        ResponseEntity<Map> ok = rest.exchange(API + "/" + THEME + "/cards/op-luffy", HttpMethod.PUT,
                new HttpEntity<>(luffy("Luffy API", 3).toString(), h), Map.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody()).containsEntry("stored", true);
        assertThat(card("op-luffy").name()).isEqualTo("Luffy API");
    }

    /** Session d'un compte qui a le rôle ADMIN en base. */
    private HttpHeaders adminSession() {
        me.blindle.accounts.UserAccount admin = new me.blindle.accounts.UserAccount(java.util.UUID.randomUUID(), "Admin", null,
                java.time.Instant.now());
        admin.role(me.blindle.accounts.UserAccount.ADMIN);
        accounts.save(admin);
        return TestLogin.session(rest, admin.id());
    }
}
