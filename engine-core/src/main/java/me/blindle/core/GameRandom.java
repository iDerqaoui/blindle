package me.blindle.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Seule source d'aléa du moteur. Toujours construite depuis la seed de la partie,
 * dérivée par une clé de contexte (« deck », « tie:round-3 »…) pour que chaque tirage
 * soit reproductible indépendamment de l'ordre des autres.
 */
public final class GameRandom {

    private final Random random;

    private GameRandom(long seed) {
        this.random = new Random(seed);
    }

    public static GameRandom of(long seed) {
        return new GameRandom(seed);
    }

    /** Aléa déterministe propre à un contexte donné de la partie. */
    public static GameRandom derive(long seed, String context) {
        long h = seed;
        for (int i = 0; i < context.length(); i++) {
            h = 31 * h + context.charAt(i);
        }
        // Mélange SplitMix64 pour décorréler des contextes proches.
        h += 0x9E3779B97F4A7C15L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return new GameRandom(h ^ (h >>> 31));
    }

    public int nextInt(int bound) {
        return random.nextInt(bound);
    }

    public double nextDouble() {
        return random.nextDouble();
    }

    public long nextLong() {
        return random.nextLong();
    }

    public <T> List<T> shuffled(List<T> items) {
        List<T> copy = new ArrayList<>(items);
        Collections.shuffle(copy, random);
        return List.copyOf(copy);
    }

    public <T> T pick(List<T> items) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Liste vide");
        }
        return items.get(random.nextInt(items.size()));
    }
}
