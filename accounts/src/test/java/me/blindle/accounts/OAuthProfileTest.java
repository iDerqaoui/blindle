package me.blindle.accounts;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthProfileTest {

    @Test
    void readsGoogleUserInfo() {
        OAuthProfile p = OAuthProfile.from("google", Map.of("sub", "1234", "email", "Alice@Example.com",
                "email_verified", true, "name", "Alice Martin", "picture", "https://lh3/pic.jpg"));

        assertThat(p).isEqualTo(new OAuthProfile("google", "1234", "alice@example.com", true,
                "Alice Martin", "https://lh3/pic.jpg"));
    }

    @Test
    void readsDiscordUserPreferringGlobalName() {
        Map<String, Object> a = new HashMap<>(Map.of("id", "8035", "username", "bilal_99", "global_name", "Bilal",
                "email", "b@example.com", "verified", false, "avatar", "abc"));
        OAuthProfile p = OAuthProfile.from("discord", a);

        assertThat(p.name()).isEqualTo("Bilal");
        assertThat(p.emailVerified()).isFalse();
        assertThat(p.avatarUrl()).isEqualTo("https://cdn.discordapp.com/avatars/8035/abc.png?size=128");

        a.put("global_name", null);
        a.remove("avatar");
        assertThat(OAuthProfile.from("discord", a).name()).isEqualTo("bilal_99");
        assertThat(OAuthProfile.from("discord", a).avatarUrl()).isNull();
    }

    @Test
    void nicknameFitsTheGameLimit() {
        assertThat(OAuthProfile.nickname("  Une personne au nom très long  ")).isEqualTo("Une personne au nom");
        assertThat(OAuthProfile.nickname(" ")).isEqualTo("Player");
        assertThat(OAuthProfile.nickname(null)).isEqualTo("Player");
    }

    @Test
    void unknownProviderIsRejected() {
        assertThatThrownBy(() -> OAuthProfile.from("github", Map.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
