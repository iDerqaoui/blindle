package me.blindle.modes;

import me.blindle.core.GameException;
import me.blindle.core.GameRandom;
import me.blindle.core.Phase;
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
 * {@code vote} : chaque joueur humain vote pour la meilleure équipe, sauf la sienne.
 * Score = nombre de voix. Égalité : bonus des règles (synergies…), puis budget restant, puis seed.
 */
@Component
public class VoteVerdictMode implements VerdictMode {

    public static final String ID = "vote";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Set<String> requiredThemeFields() {
        return Set.of();
    }

    @Override
    public boolean usesVotes() {
        return true;
    }

    @Override
    public void validateVote(GameState state, Vote vote, List<Vote> existing) {
        if (state.phase() != Phase.VERDICT) {
            throw new GameException("WRONG_PHASE", "Le vote n'est pas ouvert");
        }
        PlayerState voter = state.player(vote.voterId())
                .orElseThrow(() -> new GameException("UNKNOWN_PLAYER", "Votant inconnu"));
        if (voter.bot()) {
            throw new GameException("BOT_CANNOT_VOTE", "Les bots ne votent pas");
        }
        if (state.player(vote.teamOwnerId()).isEmpty()) {
            throw new GameException("UNKNOWN_PLAYER", "Équipe inconnue");
        }
        if (vote.voterId().equals(vote.teamOwnerId())) {
            throw new GameException("SELF_VOTE", "Impossible de voter pour ta propre équipe");
        }
        if (existing.stream().anyMatch(v -> v.voterId().equals(vote.voterId()))) {
            throw new GameException("ALREADY_VOTED", "Tu as déjà voté");
        }
    }

    @Override
    public boolean votesComplete(GameState state, List<Vote> votes) {
        long voters = state.players().stream().filter(p -> !p.bot()).count();
        return votes.stream().map(Vote::voterId).distinct().count() >= voters;
    }

    @Override
    public GameResult compute(GameState state, List<Vote> votes) {
        List<Vote> valid = new ArrayList<>();
        for (Vote v : votes) {
            validateVote(state, v, valid);
            valid.add(v);
        }
        Map<String, Integer> scores = new HashMap<>();
        for (PlayerState p : state.players()) {
            scores.put(p.id(), 0);
        }
        for (Vote v : valid) {
            scores.merge(v.teamOwnerId(), 1, Integer::sum);
        }
        Map<String, Integer> budgets = new HashMap<>();
        Map<String, Integer> bonuses = new HashMap<>();
        state.players().forEach(p -> {
            budgets.put(p.id(), p.budget());
            bonuses.put(p.id(), p.bonus());
        });

        // Ordre aléatoire seedé, puis tri stable : voix, bonus des règles, budget restant.
        List<String> ranking = new ArrayList<>(GameRandom.derive(state.seed(), "verdict")
                .shuffled(state.players().stream().map(PlayerState::id).toList()));
        ranking.sort(Comparator.<String>comparingInt(scores::get).reversed()
                .thenComparing(Comparator.<String>comparingInt(bonuses::get).reversed())
                .thenComparing(Comparator.<String>comparingInt(budgets::get).reversed()));
        return new GameResult(ranking, scores);
    }
}
