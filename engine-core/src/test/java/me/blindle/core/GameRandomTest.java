package me.blindle.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class GameRandomTest {

    private final List<Integer> items = IntStream.range(0, 50).boxed().toList();

    @Test
    void sameSeedAndContextGiveSameSequence() {
        assertThat(GameRandom.derive(42, "deck").shuffled(items))
                .isEqualTo(GameRandom.derive(42, "deck").shuffled(items));
    }

    @Test
    void differentSeedsOrContextsDiffer() {
        List<Integer> base = GameRandom.derive(42, "deck").shuffled(items);
        assertThat(GameRandom.derive(43, "deck").shuffled(items)).isNotEqualTo(base);
        assertThat(GameRandom.derive(42, "tie").shuffled(items)).isNotEqualTo(base);
    }
}
