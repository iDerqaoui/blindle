package me.blindle.realtime.stomp;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Jeton de reconnexion : rend au joueur son identité (donc sa place, son équipe, son rôle d'hôte)
 * quand sa connexion tombe ou qu'il recharge la page. Jeton = identifiant + HMAC-SHA256 avec un secret
 * tiré au démarrage : rien n'est stocké, et un redémarrage (qui efface les rooms) invalide tout.
 * L'identifiant d'un joueur est public (vues de room) ; sans le HMAC, il ne permet pas de se faire passer
 * pour lui.
 */
@Component
public class SessionTokens {

    private static final String ALGO = "HmacSHA256";
    private final byte[] secret;

    public SessionTokens() {
        this(randomSecret());
    }

    SessionTokens(byte[] secret) {
        this.secret = secret.clone();
    }

    private static byte[] randomSecret() {
        byte[] s = new byte[32];
        new SecureRandom().nextBytes(s);
        return s;
    }

    public String issue(String playerId) {
        return playerId + "." + sign(playerId);
    }

    /** Identifiant du joueur si le jeton est authentique. */
    public Optional<String> resolve(String token) {
        if (token == null || token.length() > 200) {
            return Optional.empty();
        }
        int dot = token.lastIndexOf('.');
        if (dot <= 0) {
            return Optional.empty();
        }
        String id = token.substring(0, dot);
        byte[] expected = sign(id).getBytes(StandardCharsets.US_ASCII);
        byte[] given = token.substring(dot + 1).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, given) ? Optional.of(id) : Optional.empty();
    }

    private String sign(String playerId) {
        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(new SecretKeySpec(secret, ALGO));
            byte[] h = mac.doFinal(playerId.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(h);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
