package me.blindle.modes.bot;

import org.junit.jupiter.api.Test;

import java.util.List;

import static me.blindle.modes.bot.BotFixtures.ALL;
import static me.blindle.modes.bot.BotFixtures.HI;
import static me.blindle.modes.bot.BotFixtures.LO;
import static org.assertj.core.api.Assertions.assertThat;

/** Verdict « points » : le bot valorise une carte à 10 pièces par point de rareté. */
class BotEstimatorPointsTest {

    private static BotView drawer(String verdictMode) {
        return new BotView("bot-1", 42L, "drawer", true, 1, 10, 1000, 1000, 5, 5, 0, 0, List.of(), 1, 0,
                null, List.of(), ALL, HI, verdictMode);
    }

    @Test
    void drawnLegendaryIsWorthAboutSixHundredUnderPoints() {
        assertThat(BotEstimator.estimate(drawer("points"))).isBetween(540.0, 660.0);
    }

    @Test
    void otherVerdictsKeepCardValuation() {
        // légendaire, stat 100 : 2 × 100 + 150
        assertThat(BotEstimator.estimate(drawer("value"))).isBetween(315.0, 385.0);
    }

    @Test
    void blindEstimateUsesThePointsOfUnseenCards() {
        BotView blind = new BotView("bot-1", 42L, "drawer", true, 1, 10, 1000, 1000, 5, 5, 0, 0, List.of(), 1, 0,
                null, List.of(), List.of(HI, LO), null, "points");
        // moyenne de 600 (légendaire) et 100 (commune), ± 10 %
        assertThat(BotEstimator.estimate(blind)).isBetween(315.0, 385.0);
    }
}
