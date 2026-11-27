package me.blindle.accounts;

import java.util.Locale;
import java.util.Map;

/** Profil normalisé renvoyé par un fournisseur d'identité, quel qu'il soit. */
public record OAuthProfile(String provider, String subject, String email, boolean emailVerified,
                           String name, String avatarUrl) {

    public static final String GOOGLE = "google";
    public static final String DISCORD = "discord";
    public static final int MAX_NAME = 20;

    /** Lit les attributs bruts de l'endpoint userinfo du fournisseur. */
    public static OAuthProfile from(String provider, Map<String, Object> a) {
        switch (provider) {
            case GOOGLE:
                return new OAuthProfile(GOOGLE, str(a, "sub"), email(a), bool(a, "email_verified"),
                        name(str(a, "name"), str(a, "given_name")), str(a, "picture"));
            case DISCORD: {
                String id = str(a, "id");
                String avatar = str(a, "avatar");
                return new OAuthProfile(DISCORD, id, email(a), bool(a, "verified"),
                        name(str(a, "global_name"), str(a, "username")),
                        avatar == null ? null : "https://cdn.discordapp.com/avatars/" + id + "/" + avatar + ".png?size=128");
            }
            default:
                throw new IllegalArgumentException("Fournisseur inconnu : " + provider);
        }
    }

    /** Pseudo de jeu : le nom affiché, coupé à {@link #MAX_NAME} caractères. */
    public static String nickname(String name) {
        String n = name == null ? "" : name.strip();
        if (n.isEmpty()) {
            return "Player";
        }
        return n.length() <= MAX_NAME ? n : n.substring(0, MAX_NAME).strip();
    }

    private static String name(String preferred, String fallback) {
        return nickname(preferred != null && !preferred.isBlank() ? preferred : fallback);
    }

    private static String email(Map<String, Object> a) {
        String e = str(a, "email");
        return e == null ? null : e.toLowerCase(Locale.ROOT);
    }

    private static String str(Map<String, Object> a, String key) {
        Object v = a.get(key);
        return v == null ? null : v.toString();
    }

    private static boolean bool(Map<String, Object> a, String key) {
        Object v = a.get(key);
        return v instanceof Boolean ? (Boolean) v : "true".equals(String.valueOf(v));
    }
}
