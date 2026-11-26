package me.blindle.content;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Combinaisons de droits (§8) : une vérification par règle. */
class RightsPolicyTest {

    private static Theme.Rights rights(String profile, List<String> channels, String visibility, String images) {
        return new Theme.Rights(profile, channels, visibility, images, "Avertissement", "contact@blindle.me");
    }

    @Test
    void repositoryThemesAreConsistent() {
        new ContentRegistry("").themes().forEach(t -> assertThat(RightsPolicy.check(t.rights()))
                .as(t.id()).isEmpty());
    }

    @Test
    void anyImageIsReservedToPrivateProfile() {
        assertThat(RightsPolicy.check(rights("free", List.of("web"), "public", "any")))
                .anyMatch(e -> e.contains("imagePolicy « any » interdit"));
        assertThat(RightsPolicy.check(rights("real-people", List.of("web"), "private", "any")))
                .anyMatch(e -> e.contains("imagePolicy « any » interdit"));
        assertThat(RightsPolicy.check(rights("private", List.of("web"), "private", "any"))).isEmpty();
    }

    @Test
    void realPeopleHaveNoPhotos() {
        assertThat(RightsPolicy.check(rights("real-people", List.of("web"), "public", "licensed-only")))
                .anyMatch(e -> e.contains("pas de photos"));
        assertThat(RightsPolicy.check(rights("real-people", List.of("web"), "public", "generic-only"))).isEmpty();
    }

    @Test
    void ipThemesStayOnWebWithDisclaimerAndTakedownContact() {
        assertThat(RightsPolicy.check(rights("fictional-ip", List.of("web", "discord"), "private", "none")))
                .anyMatch(e -> e.contains("reste sur le canal web"));
        assertThat(RightsPolicy.check(rights("real-people", List.of("discord"), "private", "none")))
                .anyMatch(e -> e.contains("reste sur le canal web"));
        assertThat(RightsPolicy.check(new Theme.Rights("fictional-ip", List.of("web"), "private", "none", null, null)))
                .anyMatch(e -> e.contains("disclaimer"))
                .anyMatch(e -> e.contains("takedownContact"));
    }

    @Test
    void publicThemesNeedATakedownContact() {
        assertThat(RightsPolicy.check(new Theme.Rights("free", List.of("web"), "public", "none", null, null)))
                .containsExactly("contact de retrait « takedownContact » obligatoire");
        assertThat(RightsPolicy.check(new Theme.Rights("free", List.of("web"), "private", "none", null, null)))
                .isEmpty();
    }

    @Test
    void privateProfileIsNeverPublicNorOnDiscord() {
        assertThat(RightsPolicy.check(rights("private", List.of("web"), "public", "none")))
                .anyMatch(e -> e.contains("ne peut pas être public"));
        assertThat(RightsPolicy.check(rights("private", List.of("discord"), "private", "none")))
                .anyMatch(e -> e.contains("ne va pas sur Discord"));
    }

    @Test
    void presetWithoutChannelsInheritsItsTheme() {
        ContentRegistry content = new ContentRegistry("");
        Theme countries = content.theme("pays-du-monde").orElseThrow();
        assertThat(content.preset("solo-pays-bots").orElseThrow().channels(countries))
                .containsExactlyInAnyOrder(Channel.WEB, Channel.DISCORD);
        Theme manga = content.theme("manga-shonen").orElseThrow();
        assertThat(content.preset("manga-classique").orElseThrow().channels(manga)).containsExactly(Channel.WEB);
    }
}
