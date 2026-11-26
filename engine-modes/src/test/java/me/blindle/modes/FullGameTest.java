package me.blindle.modes;

import me.blindle.core.Fixtures;
import me.blindle.core.GameEngine;
import me.blindle.core.Phase;
import me.blindle.core.model.Bid;
import me.blindle.core.model.GameResult;
import me.blindle.core.model.GameState;
import me.blindle.core.rule.RuleEngine;
import me.blindle.core.rule.RuleParams;
import me.blindle.rules.BudgetCapRule;
import me.blindle.rules.RisingFloorRule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Partie complète : 3 joueurs, 3 rounds, budget-cap + rising-floor, sealed, vote. */
class FullGameTest {

    private static final Instant DEADLINE = Instant.parse("2026-01-01T00:00:30Z");

    private final SealedBidMode sealed = new SealedBidMode();
    private final VoteVerdictMode vote = new VoteVerdictMode();
    private final GameEngine engine = new GameEngine(new RuleEngine(List.of(
            new RuleEngine.ActiveRule(new BudgetCapRule(), RuleParams.EMPTY),
            new RuleEngine.ActiveRule(new RisingFloorRule(),
                    RuleParams.of(Map.of("base", 50, "stepPerHint", 50))))));

    private GameState play(long seed) {
        GameState s = engine.start(Fixtures.lobby(engine, seed, 3)).state();

        // Round 1 : p1 mise dès le 1er indice (plancher 50), seul enchérisseur.
        s = biddingOnNewLot(s);
        s = engine.placeBid(s, new Bid("p1", 60)).state();
        s = engine.pass(engine.pass(s, "p2").state(), "p3").state();
        s = closeAndPlace(s, "captain");

        // Round 2 : personne au 1er indice, puis p2 et p3 se battent au 2e (plancher 100).
        s = biddingOnNewLot(s);
        s = passAll(s);
        assertThat(sealed.isComplete(s)).isTrue();
        s = engine.closeBidding(s, sealed).state();
        assertThat(s.phase()).isEqualTo(Phase.HINT);
        assertThat(s.lot().floorPrice()).isEqualTo(100);
        s = engine.openBidding(s, DEADLINE).state();
        s = engine.placeBid(s, new Bid("p2", 300)).state();
        s = engine.placeBid(s, new Bid("p3", 250)).state();
        s = engine.pass(s, "p1").state();
        s = closeAndPlace(s, "right");

        // Round 3 : égalité p1/p3 à 200, la seed tranche ; résolution au timeout (p2 n'a rien fait).
        s = biddingOnNewLot(s);
        s = engine.placeBid(s, new Bid("p1", 200)).state();
        s = engine.placeBid(s, new Bid("p3", 200)).state();
        assertThat(sealed.isComplete(s)).isFalse();
        s = closeAndPlace(s, "support");

        s = engine.nextRound(s).state();
        assertThat(s.phase()).isEqualTo(Phase.VERDICT);

        List<Vote> votes = List.of(new Vote("p1", "p2"), new Vote("p2", "p1"), new Vote("p3", "p2"));
        assertThat(vote.votesComplete(s, votes)).isTrue();
        GameResult result = vote.compute(s, votes);
        return engine.finish(s, result).state();
    }

    private GameState biddingOnNewLot(GameState s) {
        s = engine.revealFirstHint(engine.nextRound(s).state()).state();
        assertThat(s.lot().floorPrice()).isEqualTo(50);
        return engine.openBidding(s, DEADLINE).state();
    }

    private GameState passAll(GameState s) {
        for (String id : s.eligibleBidders()) {
            s = engine.pass(s, id).state();
        }
        return s;
    }

    /** Clôt le palier (fin du timer simulée), révèle et place la carte du gagnant. */
    private GameState closeAndPlace(GameState s, String slotId) {
        s = engine.closeBidding(s, sealed).state();
        assertThat(s.phase()).isEqualTo(Phase.AWARDED);
        s = engine.revealCard(s).state();
        return engine.assignSlot(s, s.lot().winnerId(), slotId, s.lot().cards().get(0).id()).state();
    }

    @Test
    void threePlayersThreeRounds() {
        GameState end = play(42);

        assertThat(end.phase()).isEqualTo(Phase.FINISHED);
        assertThat(end.roundIndex()).isEqualTo(3);
        assertThat(end.player("p1").orElseThrow().team()).containsKey("captain");
        assertThat(end.player("p2").orElseThrow().team()).containsKey("right");
        assertThat(end.player("p2").orElseThrow().budget()).isEqualTo(700);
        int spent = end.players().stream().mapToInt(p -> 1000 - p.budget()).sum();
        assertThat(spent).isEqualTo(60 + 300 + 200);
        assertThat(end.result().ranking().get(0)).isEqualTo("p2");
        assertThat(end.log()).anyMatch(l -> l.contains("remporte le lot pour 300"));
    }

    @Test
    void sameSeedReplaysIdentically() {
        assertThat(play(7)).isEqualTo(play(7));
    }
}
