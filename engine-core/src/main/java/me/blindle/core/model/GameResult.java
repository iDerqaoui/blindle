package me.blindle.core.model;

import java.util.List;
import java.util.Map;

/**
 * @param ranking identifiants des joueurs, du premier au dernier
 * @param scores  score par joueur (sens défini par le mode de verdict)
 */
public record GameResult(List<String> ranking, Map<String, Integer> scores) {

    public GameResult {
        ranking = List.copyOf(ranking);
        scores = Map.copyOf(scores);
    }
}
