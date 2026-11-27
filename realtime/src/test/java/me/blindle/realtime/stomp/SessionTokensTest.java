package me.blindle.realtime.stomp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Jeton de reconnexion : authentique, infalsifiable sans le secret du serveur. */
class SessionTokensTest {

    private final SessionTokens tokens = new SessionTokens(new byte[32]);

    @Test
    void aTokenGivesBackItsPlayer() {
        assertThat(tokens.resolve(tokens.issue("p-123"))).contains("p-123");
    }

    @Test
    void forgedOrForeignTokensAreRefused() {
        String token = tokens.issue("p-123");
        assertThat(tokens.resolve("p-456" + token.substring(token.indexOf('.')))).isEmpty();
        assertThat(tokens.resolve("p-123.abc")).isEmpty();
        assertThat(tokens.resolve("p-123")).isEmpty();
        assertThat(tokens.resolve(null)).isEmpty();
        byte[] other = new byte[32];
        other[0] = 1;
        assertThat(new SessionTokens(other).resolve(token)).isEmpty(); // autre serveur, ou redémarré
    }
}
