package me.blindle.modes;

import me.blindle.core.Fixtures;
import me.blindle.core.GameEngine;
import me.blindle.core.GameException;
import me.blindle.core.Phase;
import me.blindle.core.model.GameResult;
import me.blindle.core.model.GameState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VoteVerdictModeTest {

    private final VoteVerdictMode mode = new VoteVerdictMode();

    /** Trois joueurs en VERDICT ; budgets p1=900, p2=1000, p3=1000. */
    private GameState verdict() {
        GameEngine engine = new GameEngine();
        GameState s = engine.start(Fixtures.lobby(engine, 5, 3)).state();
        return s.withPhase(Phase.VERDICT).withPlayer("p1", p -> p.withBudget(900));
    }

    @Test
    void mostVotedTeamWins() {
        GameResult r = mode.compute(verdict(), List.of(
                new Vote("p1", "p3"), new Vote("p2", "p3"), new Vote("p3", "p1")));

        assertThat(r.ranking()).containsExactly("p3", "p1", "p2");
        assertThat(r.scores()).containsEntry("p3", 2).containsEntry("p1", 1).containsEntry("p2", 0);
    }

    @Test
    void tieIsBrokenByRemainingBudgetThenSeed() {
        GameState s = verdict();
        // p1, p2, p3 : une voix chacun ; p1 a le moins de budget.
        List<Vote> votes = List.of(new Vote("p1", "p2"), new Vote("p2", "p3"), new Vote("p3", "p1"));
        GameResult r = mode.compute(s, votes);

        assertThat(r.ranking().get(2)).isEqualTo("p1");
        assertThat(mode.compute(s, votes)).isEqualTo(r);
    }

    @Test
    void invalidVotesAreRefused() {
        GameState s = verdict();
        assertThatThrownBy(() -> mode.validateVote(s, new Vote("p1", "p1"), List.of()))
                .hasFieldOrPropertyWithValue("code", "SELF_VOTE");
        assertThatThrownBy(() -> mode.validateVote(s, new Vote("p1", "p2"), List.of(new Vote("p1", "p3"))))
                .hasFieldOrPropertyWithValue("code", "ALREADY_VOTED");
        assertThatThrownBy(() -> mode.validateVote(s, new Vote("p1", "ghost"), List.of()))
                .isInstanceOf(GameException.class);
        assertThatThrownBy(() -> mode.validateVote(s.withPhase(Phase.SLOT), new Vote("p1", "p2"), List.of()))
                .hasFieldOrPropertyWithValue("code", "WRONG_PHASE");
    }

    @Test
    void votesCompleteWhenEveryHumanVoted() {
        GameState s = verdict();
        assertThat(mode.votesComplete(s, List.of(new Vote("p1", "p2"), new Vote("p2", "p1")))).isFalse();
        assertThat(mode.votesComplete(s, List.of(new Vote("p1", "p2"), new Vote("p2", "p1"),
                new Vote("p3", "p1")))).isTrue();
    }
}
