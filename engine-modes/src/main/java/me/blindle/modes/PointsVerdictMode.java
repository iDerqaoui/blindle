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
 * {@code points} : score = points de rareté des cartes de l'équipe ({@link RarityPoints})
 * − {@link #EMPTY_SLOT_PENALTY} par slot vide.
 * <p>
 * L'or sert uniquement à acheter : l'or non dépensé ne rapporte aucun point (il ne sert qu'à
 * départager). Un slot vide coûte plus qu'une commune ne rapporte : attendre les légendaires en
 * laissant filer le reste devient risqué.
 * Égalité : bonus des règles, puis budget restant, puis seed.
 * Exige {@code rarity} sur toutes les cartes.
 */
@Component
public class PointsVerdictMode implements VerdictMode {

    public static final String ID = "points";
    public static final int EMPTY_SLOT_PENALTY = 25;

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
            scores.put(p.id(), score(state, p));
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

    public static int score(GameState state, PlayerState player) {
        int emptySlots = Math.max(0, state.slots().size() - player.team().size());
        return teamPoints(state, player) - EMPTY_SLOT_PENALTY * emptySlots;
    }

    public static int teamPoints(GameState state, PlayerState player) {
        List<Card> cards = player.team().values().stream()
                .map(id -> state.card(id).orElseThrow()).toList();
        return RarityPoints.sum(cards);
    }
}
