package me.blindle.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhaseMachineTest {

    private final PhaseMachine machine = new PhaseMachine();

    @Test
    void roundSequenceIsAllowed() {
        Phase[] path = {Phase.LOBBY, Phase.STARTED, Phase.DRAW, Phase.HINT, Phase.BIDDING,
                Phase.AWARDED, Phase.REVEALED, Phase.SLOT, Phase.VERDICT, Phase.FINISHED};
        for (int i = 0; i < path.length - 1; i++) {
            assertThat(machine.transition(path[i], path[i + 1])).isEqualTo(path[i + 1]);
        }
    }

    @Test
    void biddingWithoutWinnerMayGoBackToHintOrEndTheRound() {
        assertThat(machine.canTransition(Phase.BIDDING, Phase.HINT)).isTrue();
        assertThat(machine.canTransition(Phase.BIDDING, Phase.DRAW)).isTrue();
        assertThat(machine.canTransition(Phase.BIDDING, Phase.VERDICT)).isTrue();
    }

    @Test
    void invalidTransitionsAreRefused() {
        assertThatThrownBy(() -> machine.transition(Phase.LOBBY, Phase.BIDDING))
                .isInstanceOf(IllegalPhaseTransitionException.class);
        assertThatThrownBy(() -> machine.transition(Phase.HINT, Phase.AWARDED))
                .isInstanceOf(IllegalPhaseTransitionException.class);
        assertThatThrownBy(() -> machine.transition(Phase.FINISHED, Phase.LOBBY))
                .isInstanceOf(IllegalPhaseTransitionException.class);
        assertThat(machine.canTransition(Phase.REVEALED, Phase.DRAW)).isFalse();
    }
}
