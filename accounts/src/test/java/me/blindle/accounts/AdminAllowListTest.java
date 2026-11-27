package me.blindle.accounts;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAllowListTest {

    private final AdminAllowList admins = new AdminAllowList(" Boss@Example.com , discord:8035 ,");

    private static OAuthProfile profile(String provider, String subject, String email, boolean verified) {
        return new OAuthProfile(provider, subject, email, verified, "x", null);
    }

    @Test
    void verifiedEmailOnTheListIsAdmin() {
        assertThat(admins.isAdmin(profile("google", "1", "boss@example.com", true))).isTrue();
        assertThat(admins.isAdmin(profile("discord", "2", "boss@example.com", true))).isTrue();
    }

    @Test
    void unverifiedEmailIsNotEnough() {
        assertThat(admins.isAdmin(profile("discord", "2", "boss@example.com", false))).isFalse();
    }

    @Test
    void providerIdentityOnTheListIsAdmin() {
        assertThat(admins.isAdmin(profile("discord", "8035", null, false))).isTrue();
        assertThat(admins.isAdmin(profile("google", "8035", null, false))).isFalse();
    }

    @Test
    void emptyListHasNoAdmin() {
        assertThat(new AdminAllowList("").isAdmin(profile("google", "1", "boss@example.com", true))).isFalse();
    }
}
