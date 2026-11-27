package me.blindle.realtime.solo.who;

import me.blindle.core.GameException;
import me.blindle.modes.RarityPoints;
import me.blindle.realtime.solo.who.WhoLineup.WhoCard;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Partie « Who's That? » d'un joueur pour un jour. Tout est calculé à l'heure serveur passée en
 * argument : palier de netteté, prix, expiration (principe 1). Non thread-safe : le service synchronise.
 * <p>
 * Une carte à la fois : l'image se précise d'un palier toutes les {@link #STEP}, le prix monte avec
 * ({@link #PRICES}) ; au-delà de {@link #DURATION} la carte part, gratuitement.
 * <p>
 * Deviner : un nom juste achète la carte au prix courant, points doublés ; un nom faux (de l'univers)
 * rend l'image plus nette d'un palier, donc la carte plus chère.
 */
public final class WhoRun {

    public static final int[] PRICES = {40, 70, 110, 160, 230, 310, 400};
    public static final int LEVELS = PRICES.length;
    public static final Duration STEP = Duration.ofMillis(1500);
    /** Dernier palier atteint à 9 s, puis 3 s pour se décider. */
    public static final Duration DURATION = STEP.multipliedBy(LEVELS - 1).plusSeconds(3);
    public static final int BUDGET = 1000;
    public static final int SLOTS = 5;
    public static final int JACKPOT_LAG = 2;
    public static final int JACKPOT_BONUS = 30;
    public static final int EMPTY_SLOT_PENALTY = 25;

    /**
     * Issue d'une carte. {@code skipped} : jamais montrée (équipe pleine ou or épuisé avant son tour).
     *
     * @param level  palier au moment de l'achat (ou du départ)
     * @param misses mauvais noms proposés pour cette carte
     */
    public record Outcome(int index, WhoCard card, boolean bought, int price, int level, boolean called,
                          boolean jackpot, boolean skipped, int misses) {
    }

    private final String key;
    private final String name;
    private final WhoLineup lineup;
    private final List<Outcome> outcomes = new ArrayList<>();
    private int index = -1;
    private Instant startedAt;
    private boolean live;
    private int budget = BUDGET;
    private Integer rank;
    /** Mauvais noms proposés pour la carte en jeu (chacun la rend plus nette d'un palier). */
    private final List<String> wrong = new ArrayList<>();

    public WhoRun(String key, String name, WhoLineup lineup) {
        this.key = key;
        this.name = name;
        this.lineup = lineup;
    }

    public String key() {
        return key;
    }

    public String name() {
        return name;
    }

    public WhoLineup lineup() {
        return lineup;
    }

    public List<Outcome> outcomes() {
        return List.copyOf(outcomes);
    }

    public int index() {
        return index;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public int budget() {
        return budget;
    }

    public Integer rank() {
        return rank;
    }

    public List<String> wrongGuesses() {
        return List.copyOf(wrong);
    }

    void rank(int rank) {
        this.rank = rank;
    }

    /** Une carte est à l'écran (après {@link #settle}). */
    public boolean live(Instant now) {
        settle(now);
        return live;
    }

    public boolean finished() {
        return outcomes.size() == lineup.cards().size();
    }

    public int teamSize() {
        return (int) outcomes.stream().filter(Outcome::bought).count();
    }

    // ------------------------------------------------------------------ actions

    /** Montre la carte suivante ; si plus rien ne peut s'acheter, les cartes restantes sont sautées. */
    public void next(Instant now) {
        settle(now);
        if (live) {
            throw new GameException("WHO_LIVE", "Une carte est déjà en jeu");
        }
        if (finished()) {
            throw new GameException("WHO_FINISHED", "Le défi du jour est terminé");
        }
        if (teamSize() >= SLOTS || budget < PRICES[0]) {
            for (int i = index + 1; i < lineup.cards().size(); i++) {
                outcomes.add(new Outcome(i, lineup.cards().get(i), false, 0, 0, false, i == lineup.jackpotIndex(), true, 0));
            }
            index = lineup.cards().size() - 1;
            return;
        }
        index++;
        startedAt = now;
        live = true;
        wrong.clear();
    }

    /** Achète la carte au prix du palier courant, sans la nommer. */
    public Outcome buy(Instant now) {
        requireLive(now);
        return purchase(now, false);
    }

    /**
     * « Call it! » : un nom de l'univers. Juste : achat au prix courant, points doublés. Faux : l'image
     * se précise d'un palier (et le prix monte) ; renvoie null. Un même mauvais nom ne compte qu'une fois.
     */
    public Outcome guess(Instant now, String name) {
        requireLive(now);
        String n = name == null ? "" : normalize(name);
        String known = lineup.roster().stream().filter(r -> normalize(r).equals(n)).findFirst()
                .orElseThrow(() -> new GameException("WHO_UNKNOWN_NAME", "Choisis un nom dans les suggestions"));
        if (matches(known, current().name())) {
            return purchase(now, true);
        }
        if (wrong.stream().noneMatch(w -> normalize(w).equals(n))) {
            wrong.add(known);
        }
        return null;
    }

    private Outcome purchase(Instant now, boolean called) {
        int price = price(now);
        if (price > budget) {
            throw new GameException("WHO_TOO_EXPENSIVE", "Pas assez d'or");
        }
        budget -= price;
        Outcome o = new Outcome(index, current(), true, price, level(now), called,
                index == lineup.jackpotIndex(), false, wrong.size());
        outcomes.add(o);
        live = false;
        return o;
    }

    /** Laisse partir la carte : gratuit. */
    public Outcome pass(Instant now) {
        requireLive(now);
        return leave(level(now));
    }

    // ------------------------------------------------------------------ calculs

    /** Palier atteint (0 à {@link #LEVELS} − 1) : le temps écoulé, plus un palier par mauvais nom. */
    public int level(Instant now) {
        long elapsed = Math.max(0, Duration.between(startedAt, now).toMillis());
        return (int) Math.min(LEVELS - 1, elapsed / STEP.toMillis() + wrong.size());
    }

    /** Palier de netteté de l'image : le jackpot reste flou {@link #JACKPOT_LAG} paliers de plus. */
    public int imageLevel(Instant now) {
        int level = level(now);
        return index == lineup.jackpotIndex() ? Math.max(0, level - JACKPOT_LAG) : level;
    }

    public int price(Instant now) {
        return PRICES[level(now)];
    }

    public Instant expiresAt() {
        return startedAt.plus(DURATION);
    }

    public WhoCard current() {
        return lineup.cards().get(index);
    }

    /** Points de rareté (×2 si nommé) + jackpot − pénalité par slot vide. */
    public int score() {
        int score = 0;
        for (Outcome o : outcomes) {
            if (o.bought()) {
                int points = RarityPoints.POINTS.getOrDefault(o.card().rarity(), 0);
                score += o.called() ? 2 * points : points;
                score += o.jackpot() ? JACKPOT_BONUS : 0;
            }
        }
        return score - EMPTY_SLOT_PENALTY * Math.max(0, SLOTS - teamSize());
    }

    /** Nom juste : même nom complet, sans tenir compte de la casse, des accents ni de la ponctuation. */
    static boolean matches(String guess, String name) {
        if (guess == null || guess.isBlank()) {
            return false;
        }
        return normalize(guess).equals(normalize(name));
    }

    private static String normalize(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /** La carte en jeu a expiré : elle part, gratuitement. */
    private void settle(Instant now) {
        if (live && !now.isBefore(expiresAt())) {
            leave(LEVELS - 1);
        }
    }

    private Outcome leave(int level) {
        Outcome o = new Outcome(index, current(), false, 0, level, false, index == lineup.jackpotIndex(), false,
                wrong.size());
        outcomes.add(o);
        live = false;
        return o;
    }

    private void requireLive(Instant now) {
        settle(now);
        if (!live) {
            throw new GameException("WHO_NOT_LIVE", "Aucune carte en jeu");
        }
        if (teamSize() >= SLOTS) {
            throw new GameException("WHO_TEAM_FULL", "Ton équipe est complète");
        }
    }
}
