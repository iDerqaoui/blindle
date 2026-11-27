package me.blindle.curation;

import me.blindle.curation.RarityCalculator.Candidate;
import me.blindle.curation.RarityCalculator.Ranked;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RarityCalculatorTest {

    private static Candidate c(int id, String tier, Integer favourites) {
        return new Candidate(id, "Perso " + id, tier, favourites, "Supporting", "https://img/" + id + ".jpg");
    }

    /** 50 personnages de rang D, favoris décroissants : l'ordre suit la popularité. */
    private static List<Candidate> cast() {
        List<Candidate> list = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            list.add(c(i, "D", 100_000 / i));
        }
        return list;
    }

    @Test
    void deckOfFortyFollowsFixedQuotas() {
        List<Ranked> ranked = RarityCalculator.rank(cast());

        Map<String, Long> counts = ranked.stream().filter(Ranked::inDeck)
                .collect(Collectors.groupingBy(Ranked::rarity, Collectors.counting()));
        assertThat(counts).containsEntry("legendary", 4L).containsEntry("epic", 8L)
                .containsEntry("rare", 12L).containsEntry("common", 16L);
        assertThat(ranked.stream().filter(r -> !r.inDeck())).hasSize(10).allMatch(r -> r.rank() == -1);
        assertThat(ranked.get(0).candidate().malId()).isEqualTo(1);
        assertThat(ranked.get(0).popularity()).isEqualTo(100);
    }

    @Test
    void powerRankLiftsAnUnpopularCharacter() {
        List<Candidate> list = cast();
        list.set(49, c(50, "S", 2000));

        Map<Integer, Ranked> byId = RarityCalculator.rank(list).stream()
                .collect(Collectors.toMap(r -> r.candidate().malId(), Function.identity()));
        // puissance 100 : max(pop, 100) + 0,25 × pop dépasse tous les rangs D
        assertThat(byId.get(50).rank()).isZero();
        assertThat(byId.get(50).rarity()).isEqualTo("legendary");
        assertThat(byId.get(50).score()).isEqualTo(100 + 0.25 * byId.get(50).popularity());
    }

    @Test
    void scoreTieIsBrokenByFavourites() {
        // même popularité arrondie (100) et même rang : le plus de favoris passe devant
        List<Ranked> ranked = RarityCalculator.rank(List.of(c(1, "A", 9990), c(2, "A", 10000)));
        assertThat(ranked).extracting(r -> r.candidate().malId()).containsExactly(2, 1);
    }

    @Test
    void charactersWithoutMalDataAreOutOfTheDeck() {
        Candidate noData = c(7, "S", null);
        Candidate noImage = new Candidate(8, "Sans image", "S", 5000, "Main", null);
        Candidate placeholder = new Candidate(9, "Inconnu", "S", 5000, "Main", "https://cdn/questionmark_23.gif");

        List<Ranked> ranked = RarityCalculator.rank(List.of(c(1, "D", 100), noData, noImage, placeholder));

        assertThat(ranked).hasSize(4);
        assertThat(ranked.get(0).candidate().malId()).isEqualTo(1);
        assertThat(ranked.subList(1, 4)).noneMatch(Ranked::inDeck);
    }

    @Test
    void smallDeckKeepsTheProportions() {
        assertThat(RarityCalculator.rarityOf(0, 10)).isEqualTo("legendary");
        assertThat(RarityCalculator.rarityOf(1, 10)).isEqualTo("epic");
        assertThat(RarityCalculator.rarityOf(3, 10)).isEqualTo("rare");
        assertThat(RarityCalculator.rarityOf(6, 10)).isEqualTo("common");
    }

    @Test
    void unknownTierIsRejected() {
        assertThatThrownBy(() -> RarityCalculator.power("Z")).isInstanceOf(IllegalArgumentException.class);
        assertThat(RarityCalculator.power("B")).isEqualTo(70);
    }
}
