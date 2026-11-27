package me.blindle.realtime.solo.who;

import me.blindle.content.ContentRegistry;
import me.blindle.content.Theme;
import me.blindle.core.GameException;
import me.blindle.core.GameRandom;
import me.blindle.modes.RarityPoints;
import me.blindle.realtime.solo.DailyChallenge;
import me.blindle.realtime.solo.who.WhoLineup.WhoCard;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Choisit les personnages du jour, univers par univers : chaque univers illustré a son propre défi
 * quotidien. Univers éligibles : thèmes web dont toutes les cartes ont une image et une rareté connue
 * (critère générique : aucun thème n'est nommé ici).
 */
@Component
public class WhoDaily {

    public static final int CARDS = 10;
    /** Composition visée : 1 légendaire, 2 épiques, 3 rares, 4 communes (complétée au hasard sinon). */
    private static final Map<String, Integer> MIX = mix();

    private final ContentRegistry content;
    private final Map<String, WhoLineup> cache = new LinkedHashMap<>();

    public WhoDaily(ContentRegistry content) {
        this.content = content;
    }

    private static Map<String, Integer> mix() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("legendary", 1);
        m.put("epic", 2);
        m.put("rare", 3);
        m.put("common", 4);
        return m;
    }

    /** Univers qui ont un défi Who's That?, triés par nom. */
    public List<Theme> eligibleThemes() {
        return content.themes().stream()
                .filter(t -> t.rights().channels().contains("web"))
                .filter(t -> t.cards().size() >= CARDS)
                .filter(t -> t.cards().stream().allMatch(c -> c.image() != null
                        && c.rarity() != null && RarityPoints.POINTS.containsKey(c.rarity())))
                .sorted(Comparator.comparing(Theme::name))
                .toList();
    }

    /** Personnages du jour de cet univers (calculés une fois par jour et par univers). */
    public synchronized WhoLineup lineup(LocalDate date, String themeId) {
        String key = date + "|" + themeId;
        WhoLineup cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        Theme theme = eligibleThemes().stream().filter(t -> t.id().equals(themeId)).findFirst()
                .orElseThrow(() -> new GameException("WHO_UNKNOWN_UNIVERSE", "Pas de défi Who's That? pour " + themeId));
        WhoLineup lineup = build(date, theme);
        cache.keySet().removeIf(k -> k.compareTo(date.minusDays(1).toString()) < 0);
        cache.put(key, lineup);
        return lineup;
    }

    /** Défi d'un univers pour un jour : seed propre au jour et à l'univers. */
    static WhoLineup build(LocalDate date, Theme theme) {
        List<WhoCard> all = new ArrayList<>();
        theme.cards().forEach(c -> all.add(new WhoCard(c.id(), c.name(), c.rarity(), c.image(), theme.name())));
        GameRandom random = GameRandom.derive(DailyChallenge.seed(date, "who|" + theme.id()), "lineup");
        List<WhoCard> shuffled = random.shuffled(all);

        List<WhoCard> chosen = new ArrayList<>();
        MIX.forEach((rarity, n) -> shuffled.stream().filter(c -> rarity.equals(c.rarity()))
                .limit(n).forEach(chosen::add));
        for (WhoCard c : shuffled) {
            if (chosen.size() >= CARDS) {
                break;
            }
            if (!chosen.contains(c)) {
                chosen.add(c);
            }
        }
        List<WhoCard> order = random.shuffled(chosen);
        int jackpot = -1;
        for (int i = 0; i < order.size(); i++) {
            if ("legendary".equals(order.get(i).rarity())) {
                jackpot = i;
                break;
            }
        }
        TreeSet<String> roster = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        all.forEach(c -> roster.add(c.name()));
        String color = theme.art() == null ? null : theme.art().color();
        return new WhoLineup(date, theme.id(), theme.name(), color, order, jackpot, new ArrayList<>(roster));
    }
}
