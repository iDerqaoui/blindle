package me.blindle.modes;

import me.blindle.core.GameRandom;
import me.blindle.core.model.Card;
import me.blindle.core.model.GameResult;
import me.blindle.core.model.GameState;
import me.blindle.core.model.PlayerState;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code points} : score = somme des points de rareté des cartes de l'équipe ({@link RarityPoints}).
 * Les pièces ne comptent pas dans le score : le budget est la contrainte, pas l'objectif.
 * Égalité : bonus des règles, puis budget restant (le plus économe gagne), puis seed.
 * Exige {@code rarity} sur toutes les cartes.
 */
@Component
public class PointsVerdictMode implements VerdictMode {

    public static final String ID = "points";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Set<String> requiredThemeFields() {
        return Set.of("rarity");
    }

    @Override
    public GameResult compute(GameState state, List<Vote> votes) {
        Map<String, Integer> scores = new HashMap<>();
        Map<String, Integer> budgets = new HashMap<>();
        Map<String, Integer> bonuses = new HashMap<>();
        for (PlayerState p : state.players()) {
            scores.put(p.id(), teamPoints(state, p));
            budgets.put(p.id(), p.budget());
            bonuses.put(p.id(), p.bonus());
        }
        List<String> ranking = new ArrayList<>(GameRandom.derive(state.seed(), "verdict")
                .shuffled(state.players().stream().map(PlayerState::id).toList()));
        ranking.sort(Comparator.<String>comparingInt(scores::get).reversed()
                .thenComparing(Comparator.<String>comparingInt(bonuses::get).reversed())
                .thenComparing(Comparator.<String>comparingInt(budgets::get).reversed()));
        return new GameResult(ranking, scores);
    }

    public static int teamPoints(GameState state, PlayerState player) {
        List<Card> cards = player.team().values().stream()
                .map(id -> state.card(id).orElseThrow()).toList();
        return RarityPoints.sum(cards);
    }
}
