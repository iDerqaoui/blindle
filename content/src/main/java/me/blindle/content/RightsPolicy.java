package me.blindle.content;

import java.util.ArrayList;
import java.util.List;

/**
 * Combinaisons de droits cohérentes (docs/PROJECT.md §8). Repères pratiques, pas un avis juridique.
 * <ul>
 *   <li>{@code imagePolicy: any} réservé au profil {@code private} (cercle privé) ;</li>
 *   <li>{@code real-people} : pas de photos, seulement {@code generic-only} ou {@code none} ;</li>
 *   <li>{@code fictional-ip} et {@code real-people} restent sur le web, avec avertissement et contact
 *       de retrait ;</li>
 *   <li>{@code private} : visibilité privée, jamais sur Discord ;</li>
 *   <li>tout thème public donne un contact de retrait.</li>
 * </ul>
 */
public final class RightsPolicy {

    private RightsPolicy() {
    }

    /** Erreurs de cohérence, vides si les droits sont acceptables. */
    public static List<String> check(Theme.Rights rights) {
        List<String> errors = new ArrayList<>();
        String profile = rights.profile();
        String images = rights.imagePolicy();
        boolean isPublic = "public".equals(rights.visibility());
        boolean ip = "fictional-ip".equals(profile) || "real-people".equals(profile);

        if ("any".equals(images) && !"private".equals(profile)) {
            errors.add("profil « " + profile + " » avec imagePolicy « any » interdit (réservé au profil private)");
        }
        if ("real-people".equals(profile) && "licensed-only".equals(images)) {
            errors.add("profil « real-people » : pas de photos, imagePolicy « generic-only » ou « none »");
        }
        if (ip && rights.channels().contains(Channel.DISCORD.id())) {
            errors.add("un thème « " + profile + " » reste sur le canal web");
        }
        if (ip && isBlank(rights.disclaimer())) {
            errors.add("un thème « " + profile + " » exige un « disclaimer »");
        }
        if ((ip || isPublic) && isBlank(rights.takedownContact())) {
            errors.add("contact de retrait « takedownContact » obligatoire");
        }
        if ("private".equals(profile)) {
            if (isPublic) {
                errors.add("un thème au profil « private » ne peut pas être public");
            }
            if (rights.channels().contains(Channel.DISCORD.id())) {
                errors.add("un thème au profil « private » ne va pas sur Discord");
            }
        }
        return errors;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
