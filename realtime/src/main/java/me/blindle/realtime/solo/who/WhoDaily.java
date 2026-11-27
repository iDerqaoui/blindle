package me.blindle.realtime.solo.who;

import me.blindle.content.ContentRegistry;
import me.blindle.content.Theme;
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
 * Choisit les personnages du jour. Univers éligibles : thèmes illustrés dont toutes les cartes ont une
 * image et une rareté connue (critère générique : aucun thème n'est nommé ici), jouables sur le web.
 * Rotation jour par jour ; le dernier jour du cycle mélange tous les univers (« Crossover »).
 */
@Component
public class WhoDaily {

    public static final int CARDS = 10;
    /** Composition visée : 1 légendaire, 2 épiques, 3 rares, 4 communes (complétée au hasard sinon). */
    private static final Map<String, Integer> MIX = mix();
    static final String CROSSOVER = "Crossover";

    private final ContentRegistry content;
    private final Map<LocalDate, WhoLineup> cache = new LinkedHashMap<>();

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

    /** Univers éligibles, triés par identifiant (rotation stable). */
    public List<Theme> eligibleThemes() {
        return content.themes().stream()
                .filter(t -> t.rights().channels().contains("web"))
                .filter(t -> t.cards().size() >= CARDS)
                .filter(t -> t.cards().stream().allMatch(c -> c.image() != null
                        && c.rarity() != null && RarityPoints.POINTS.containsKey(c.rarity())))
                .sorted(Comparator.comparing(Theme::id))
                .toList();
    }

    public synchronized boolean available() {
        return !eligibleThemes().isEmpty();
    }

    /** Personnages du jour (calculés une fois par jour). */
    public synchronized WhoLineup lineup(LocalDate date) {
        WhoLineup cached = cache.get(date);
        if (cached != null) {
            return cached;
        }
        List<Theme> themes = eligibleThemes();
        if (themes.isEmpty()) {
            throw new IllegalStateException("Aucun univers illustré pour le défi Who's That?");
        }
        WhoLineup lineup = build(date, themes);
        cache.keySet().removeIf(d -> d.isBefore(date.minusDays(1)));
        cache.put(date, lineup);
        return lineup;
    }

    static WhoLineup build(LocalDate date, List<Theme> themes) {
        int slot = (int) Math.floorMod(date.toEpochDay(), (long) themes.size() + (themes.size() > 1 ? 1 : 0));
        boolean crossover = slot == themes.size();
        List<Theme> pool = crossover ? themes : List.of(themes.get(slot));
        List<WhoCard> all = new ArrayList<>();
        for (Theme t : pool) {
            t.cards().forEach(c -> all.add(new WhoCard(c.id(), c.name(), c.rarity(), c.image(), t.name())));
        }
        GameRandom random = GameRandom.derive(DailyChallenge.seed(date, "who"), "lineup");
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
        Theme first = pool.get(0);
        String color = crossover || first.art() == null ? null : first.art().color();
        return new WhoLineup(date, crossover ? CROSSOVER : first.name(), color, order, jackpot,
                new ArrayList<>(roster));
    }
}
