package me.blindle.modes.bot;

import me.blindle.core.model.Bid;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static me.blindle.modes.bot.BotFixtures.HI;
import static me.blindle.modes.bot.BotFixtures.MID;
import static org.assertj.core.api.Assertions.assertThat;

/** Une décision attendue par profil : vue d'entrée → offre ou passe (§7). */
class BotProfilesTest {

    private static int amount(Optional<Bid> bid) {
        assertThat(bid).isPresent();
        assertThat(bid.get().playerId()).isEqualTo("bot-1");
        assertThat(bid.get().amount() % 10).isZero();
        return bid.get().amount();
    }

    @Test
    void aggressiveBidsFromFirstHintUpTo120Percent() {
        // 1,2 × [175 ; 215]
        assertThat(amount(new AggressiveBot().decide(BotFixtures.sealed(HI, 1)))).isBetween(200, 258);
    }

    @Test
    void aggressivePassesWhenFloorIsOutOfBudget() {
        assertThat(new AggressiveBot().decide(BotFixtures.view(HI, 1, false, List.of(), 1, 5, 40, 50))).isEmpty();
    }

    @Test
    void stingyWaitsForLastHintAndCapsAt80Percent() {
        StingyBot bot = new StingyBot();
        assertThat(bot.decide(BotFixtures.sealed(HI, 1))).isEmpty();
        assertThat(bot.decide(BotFixtures.sealed(HI, 2))).isEmpty();
        // 0,8 × [315 ; 385]
        assertThat(amount(bot.decide(BotFixtures.sealed(HI, 3)))).isBetween(250, 308);
    }

    @Test
    void fanOverbidsOnFavoriteTagOnly() {
        FanBot bot = new FanBot();
        assertThat(FanBot.favoriteTag(BotFixtures.sealed(HI, 1))).isEqualTo("star");
        // Moitié des candidates (hi, lo) taguées star : 1,5 × [175 ; 215] dès le premier indice.
        assertThat(amount(bot.decide(BotFixtures.sealed(HI, 1)))).isBetween(260, 323);
        // Pas de star : attend le deuxième indice, puis 90 %.
        assertThat(bot.decide(BotFixtures.sealed(MID, 1))).isEmpty();
        assertThat(amount(bot.decide(BotFixtures.sealed(MID, 2)))).isLessThanOrEqualTo(170);
    }

    @Test
    void blufferPumpsVisibleBidsThenWithdraws() {
        BlufferBot bot = new BlufferBot();
        List<Bid> leader = List.of(new Bid("human", 100));
        assertThat(amount(bot.decide(BotFixtures.view(HI, 1, true, leader, 1, 5, 1000, 50)))).isEqualTo(110);
        assertThat(bot.decide(BotFixtures.view(HI, 3, true, leader, 1, 5, 1000, 50))).isEmpty();
        // Enchère secrète : un coup à 70 % au premier indice, puis passe.
        assertThat(amount(bot.decide(BotFixtures.sealed(HI, 1)))).isBetween(120, 151);
        assertThat(bot.decide(BotFixtures.sealed(HI, 2))).isEmpty();
    }

    @Test
    void strategistKeepsBudgetForEmptySlots() {
        StrategistBot bot = new StrategistBot();
        // Pas pressé : attend le milieu des indices.
        assertThat(bot.decide(BotFixtures.sealed(HI, 1))).isEmpty();
        assertThat(amount(bot.decide(BotFixtures.sealed(HI, 2)))).isBetween(50, 385);
        // Budget 300, plancher 100, 5 slots libres : 4 × 100 réservés → rien à dépenser.
        assertThat(bot.decide(BotFixtures.view(HI, 2, false, List.of(), 1, 5, 300, 100))).isEmpty();
        // Pressé (3 rounds pour 3 slots) : mise dès le premier indice, plafonnée par la réserve.
        int urgent = amount(bot.decide(BotFixtures.view(HI, 1, false, List.of(), 8, 3, 500, 50)));
        assertThat(urgent).isBetween(50, 400);
    }
}
