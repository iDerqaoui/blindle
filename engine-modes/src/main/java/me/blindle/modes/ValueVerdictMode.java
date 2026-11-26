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
import java.util.function.Predicate;

/**
 * {@code value} : score = valeur des cartes de l'équipe ({@link CardValuation}) − pièces dépensées
 * (cartes défaussées comprises). Égalité : bonus des règles, puis budget restant, puis seed.
 * Exige {@code stats} ou {@code rarity} sur toutes les cartes.
 */
@Component
public class ValueVerdictMode implements VerdictMode {

    public static final String ID = "value";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Set<String> requiredThemeFields() {
        return Set.of();
    }

    @Override
    public boolean supportsTheme(Predicate<String> allCardsHave) {
        return allCardsHave.test("stats") || allCardsHave.test("rarity");
    }

    @Override
    public String themeRequirement() {
        return "« stats » ou « rarity » sur toutes les cartes";
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

    public static int teamValue(GameState state, PlayerState player) {
        List<Card> cards = player.team().values().stream()
                .map(id -> state.card(id).orElseThrow()).toList();
        return CardValuation.sum(cards);
    }

    public static int spent(GameState state, PlayerState player) {
        return state.settings().budget() - player.budget();
    }

    public static int score(GameState state, PlayerState player) {
        return teamValue(state, player) - spent(state, player);
    }
}
