package me.blindle.curation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Rareté « populaire OU puissant » (portage de {@code tools/import-anime.mjs}) :
 * <ul>
 *   <li>popularité : favoris MyAnimeList, échelle log ramenée à 0–100 dans l'univers ;</li>
 *   <li>puissance : rang S/A/B/C/D → 100/85/70/50/25 ;</li>
 *   <li>score = max(popularité, puissance) + 0,25 × min ;</li>
 *   <li>les {@link #DECK_SIZE} meilleurs scores forment le paquet ; rareté par quotas fixes
 *       (10 % légendaires, 20 % épiques, 30 % rares, le reste communes).</li>
 * </ul>
 * Un personnage sans données MyAnimeList (favoris ou image) est hors classement.
 */
public final class RarityCalculator {

    public static final int DECK_SIZE = 40;
    public static final Map<String, Integer> POWER = Map.of("S", 100, "A", 85, "B", 70, "C", 50, "D", 25);
    private static final String[] RARITIES = {"legendary", "epic", "rare", "common"};
    private static final double[] CUMULATIVE = {0.1, 0.3, 0.6, 1};

    private RarityCalculator() {
    }

    /** Personnage retenu, dans l'ordre de la liste de curation. */
    public record Candidate(int malId, String name, String tier, Integer favourites, String role, String image) {

        boolean eligible() {
            return favourites != null && image != null && !image.contains("questionmark");
        }
    }

    /**
     * Résultat du classement. {@code rank} commence à 0 ; −1 et {@code rarity} nulle pour un
     * personnage hors paquet (au-delà de {@link #DECK_SIZE}) ou sans données.
     */
    public record Ranked(Candidate candidate, int popularity, int power, double score, int rank, String rarity) {

        public boolean inDeck() {
            return rarity != null;
        }
    }

    public static List<Ranked> rank(List<Candidate> candidates) {
        List<Candidate> pool = candidates.stream().filter(Candidate::eligible).toList();
        double max = Math.log(Math.max(pool.stream().mapToInt(Candidate::favourites).max().orElse(0), 2));

        List<Ranked> scored = new ArrayList<>();
        for (Candidate c : pool) {
            int popularity = (int) Math.max(1, Math.round(100 * Math.log(Math.max(c.favourites(), 1)) / max));
            int power = power(c.tier());
            double score = Math.max(popularity, power) + 0.25 * Math.min(popularity, power);
            scored.add(new Ranked(c, popularity, power, score, -1, null));
        }
        scored.sort(Comparator.comparingDouble(Ranked::score).reversed()
                .thenComparing(Comparator.comparingInt((Ranked r) -> r.candidate().favourites()).reversed()));

        int deck = Math.min(DECK_SIZE, scored.size());
        List<Ranked> result = new ArrayList<>();
        for (int i = 0; i < scored.size(); i++) {
            Ranked r = scored.get(i);
            result.add(i < deck
                    ? new Ranked(r.candidate(), r.popularity(), r.power(), r.score(), i, rarityOf(i, deck))
                    : r);
        }
        candidates.stream().filter(c -> !c.eligible())
                .forEach(c -> result.add(new Ranked(c, 0, power(c.tier()), 0, -1, null)));
        return result;
    }

    public static String rarityOf(int rank, int total) {
        for (int i = 0; i < RARITIES.length; i++) {
            if (rank < Math.round(CUMULATIVE[i] * total)) {
                return RARITIES[i];
            }
        }
        return "common";
    }

    public static int power(String tier) {
        Integer p = POWER.get(tier);
        if (p == null) {
            throw new IllegalArgumentException("Rang inconnu : " + tier + " (S, A, B, C ou D)");
        }
        return p;
    }
}
