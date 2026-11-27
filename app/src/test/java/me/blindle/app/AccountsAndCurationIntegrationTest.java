package me.blindle.app;

import me.blindle.accounts.AccountService;
import me.blindle.accounts.OAuthProfile;
import me.blindle.accounts.UserAccount;
import me.blindle.content.ContentLoader;
import me.blindle.content.Theme;
import me.blindle.curation.AnimeCharacterRepository;
import me.blindle.curation.AnimeThemeGenerator;
import me.blindle.curation.CurationService;
import me.blindle.curation.MalClient;
import me.blindle.curation.RarityCalculator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Comptes (liaison Google / Discord, accès admin) et curation des personnages, sur H2 sans réseau. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountsAndCurationIntegrationTest {

    private static final String SLUG = "naruto";

    /** Distribution factice : 50 personnages, favoris décroissants, les 3 premiers « Main ». */
    @TestConfiguration
    static class FakeMal {
        @Bean
        @Primary
        MalClient fakeMalClient() {
            return new MalClient() {
                @Override
                public List<MalCharacter> cast(List<Integer> animeIds) {
                    List<MalCharacter> list = new ArrayList<>();
                    for (int i = 1; i <= 50; i++) {
                        list.add(new MalCharacter(i, "Nom" + i + ", Prenom" + i, 100_000 / i,
                                i <= 3 ? "Main" : "Supporting", "https://img.test/" + i + ".jpg"));
                    }
                    return list;
                }

                @Override
                public Optional<AnimeInfo> anime(int animeId) {
                    return Optional.of(new AnimeInfo("Naruto (test)", "https://img.test/cover.jpg"));
                }
            };
        }
    }

    @Autowired
    TestRestTemplate rest;
    @Autowired
    CurationService curation;
    @Autowired
    AnimeThemeGenerator generator;
    @Autowired
    AnimeCharacterRepository characters;
    @Autowired
    AccountService accounts;

    @TempDir
    Path content;

    @AfterEach
    void clean() {
        characters.deleteAll();
    }

    /** Dossier de contenu temporaire : fichier de curation de 45 personnages (ids 1–45, rang D). */
    private void curationFile() throws IOException {
        Files.createDirectories(content.resolve("curation"));
        String chars = java.util.stream.IntStream.rangeClosed(1, 45)
                .mapToObj(i -> "[" + i + ", \"Perso " + i + "\", \"D\"]").collect(Collectors.joining(","));
        Files.writeString(content.resolve("curation/anime-" + SLUG + ".json"),
                "{\"universe\":\"" + SLUG + "\",\"note\":\"test\",\"characters\":[" + chars + "]}");
        Files.createDirectories(content.resolve("themes"));
        Files.createDirectories(content.resolve("presets"));
    }

    private Map<Integer, RarityCalculator.Ranked> ranking() {
        return curation.ranking(SLUG).stream().collect(Collectors.toMap(r -> r.candidate().malId(), r -> r));
    }

    @Test
    void generatedThemeAndPresetPassContentValidation() throws IOException {
        curationFile();
        generator.generate(content, SLUG);

        ContentLoader.Content loaded = new ContentLoader().load(content);
        Theme theme = loaded.themes().get("anime-" + SLUG);
        assertThat(theme.cards()).hasSize(40);
        assertThat(theme.cards().stream().filter(c -> "legendary".equals(c.rarity()))).hasSize(4);
        assertThat(theme.cards().get(0).name()).isEqualTo("Perso 1");
        assertThat(theme.art().cover()).isEqualTo("https://img.test/cover.jpg");
        assertThat(loaded.presets().get("anime-" + SLUG).verdictMode()).isEqualTo("points");

        // second démarrage : rien n'est repris du fichier, la base fait foi
        curation.edit(SLUG, 45, "Héros caché", "S", null);
        generator.generate(content, SLUG);
        Theme again = new ContentLoader().load(content).themes().get("anime-" + SLUG);
        assertThat(again.cards().get(0).name()).isEqualTo("Héros caché");
    }

    @Test
    void tierChangeIsVisibleInThePreview() throws IOException {
        curationFile();
        curation.seedFromFile(SLUG, content.resolve("curation/anime-" + SLUG + ".json"));
        curation.fillMalData(SLUG);
        assertThat(ranking().get(45).inDeck()).isFalse();

        curation.edit(SLUG, 45, null, "S", UUID.randomUUID());

        assertThat(ranking().get(45).rarity()).isEqualTo("legendary");
        assertThatThrownBy(() -> curation.edit(SLUG, 45, null, "Z", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addSearchAndRemoveCharacters() throws IOException {
        curationFile();
        curation.seedFromFile(SLUG, content.resolve("curation/anime-" + SLUG + ".json"));

        // la recherche ne propose que les personnages pas encore retenus, noms remis dans l'ordre
        assertThat(curation.search(SLUG, "")).extracting(MalClient.MalCharacter::malId)
                .containsExactly(46, 47, 48, 49, 50);
        assertThat(curation.search(SLUG, "prenom47 nom47")).extracting(MalClient.MalCharacter::name)
                .containsExactly("Prenom47 Nom47");

        curation.add(SLUG, 47, null, "a", null);
        assertThat(ranking().get(47).candidate().name()).isEqualTo("Prenom47 Nom47");
        assertThat(ranking().get(47).candidate().tier()).isEqualTo("A");
        assertThatThrownBy(() -> curation.add(SLUG, 47, null, "A", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> curation.add(SLUG, 999, null, "A", null)).isInstanceOf(IllegalArgumentException.class);

        curation.remove(SLUG, 47);
        assertThat(ranking()).doesNotContainKey(47);
    }

    @Test
    void adminApiNeedsAnAdminSession() {
        assertThat(rest.getForEntity("/api/admin/curation/universes", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        ResponseEntity<String> me = rest.getForEntity("/api/me", String.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(rest.getForObject("/api/auth/providers", List.class)).isEmpty();
        // le jeu reste ouvert sans compte
        assertThat(rest.getForEntity("/api/presets", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void googleAndDiscordShareAnAccountOnlyThroughAVerifiedEmail() {
        String email = "same-" + UUID.randomUUID() + "@example.com";
        UserAccount google = accounts.signIn(new OAuthProfile("google", "g-" + email, email, true, "Alice", null));
        UserAccount discord = accounts.signIn(new OAuthProfile("discord", "d-" + email, email, true, "alice_d", null));
        UserAccount unverified = accounts.signIn(new OAuthProfile("discord", "u-" + email, email, false, "Mallory", null));

        assertThat(discord.id()).isEqualTo(google.id());
        assertThat(unverified.id()).isNotEqualTo(google.id());
        assertThat(accounts.signIn(new OAuthProfile("google", "g-" + email, email, true, "Alice", null)).id())
                .isEqualTo(google.id());

        assertThat(accounts.rename(google.id(), "  Ali  ").displayName()).isEqualTo("Ali");
        assertThatThrownBy(() -> accounts.rename(google.id(), "x".repeat(21))).isInstanceOf(IllegalArgumentException.class);
    }
}
