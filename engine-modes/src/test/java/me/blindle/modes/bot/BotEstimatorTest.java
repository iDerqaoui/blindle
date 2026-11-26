package me.blindle.modes.bot;

import org.junit.jupiter.api.Test;

import static me.blindle.modes.bot.BotFixtures.HI;
import static me.blindle.modes.bot.BotFixtures.LO;
import static me.blindle.modes.bot.BotFixtures.MID;
import static org.assertj.core.api.Assertions.assertThat;

class BotEstimatorTest {

    @Test
    void candidatesMatchEveryRevealedHint() {
        assertThat(BotEstimator.candidates(BotFixtures.sealed(HI, 1))).containsExactlyInAnyOrder(HI, LO);
        assertThat(BotEstimator.candidates(BotFixtures.sealed(HI, 2))).containsExactly(HI);
        assertThat(BotEstimator.candidates(BotFixtures.sealed(MID, 1))).containsExactly(MID);
    }

    @Test
    void confidenceGrowsWithHints() {
        // Prior = (350 + 65 + 150) / 3 ≈ 188,3. Palier 1 : 1/3 × 207,5 + 2/3 × 188,3 ≈ 194,7 (± 10 %).
        assertThat(BotEstimator.estimate(BotFixtures.sealed(HI, 1))).isBetween(175.0, 215.0);
        // Dernier palier : seule hi reste, confiance totale → 350 (± 10 %).
        assertThat(BotEstimator.estimate(BotFixtures.sealed(HI, 3))).isBetween(315.0, 385.0);
    }

    @Test
    void estimateIsDeterministic() {
        assertThat(BotEstimator.estimate(BotFixtures.sealed(HI, 2)))
                .isEqualTo(BotEstimator.estimate(BotFixtures.sealed(HI, 2)));
    }
}
