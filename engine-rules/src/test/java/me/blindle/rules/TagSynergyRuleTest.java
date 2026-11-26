package me.blindle.rules;

import me.blindle.core.GameEngine;
import me.blindle.core.event.GameEvent;
import me.blindle.core.model.Card;
import me.blindle.core.model.GameSettings;
import me.blindle.core.model.GameState;
import me.blindle.core.model.Hint;
import me.blindle.core.model.SlotDef;
import me.blindle.core.rule.RuleParams;
import me.blindle.core.rule.RuleResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TagSynergyRuleTest {

    private static Card card(String id, String... tags) {
        return new Card(id, id, Set.of(tags), Map.of(), null, false,
                List.of(new Hint(1, "text", "a"), new Hint(2, "text", "b")));
    }

    @Test
    void bonusPerSharedTagReachingMinCount() {
        GameEngine engine = new GameEngine();
        List<Card> cards = List.of(card("a", "pirate", "fire"), card("b", "pirate"), card("c", "pirate", "fire"),
                card("d", "ninja"));
        GameState s = engine.create("g", 1, new GameSettings(1000, 3, 3, 2, 30),
                List.of(new SlotDef("s1", "1"), new SlotDef("s2", "2"), new SlotDef("s3", "3")), cards);
        s = engine.addPlayer(s, "p1", "Alice", false).state();
        s = engine.addPlayer(s, "p2", "Bilal", false).state();
        s = s.withPlayer("p1", p -> p.withCardInSlot("s1", "a").withCardInSlot("s2", "b").withCardInSlot("s3", "c"))
                .withPlayer("p2", p -> p.withCardInSlot("s1", "d"));

        RuleResult r = new TagSynergyRule().apply(new GameEvent.VerdictRequested(), s,
                RuleParams.of(Map.of("minCount", 2, "bonus", 5)));

        GameState out = ((RuleResult.Continue) r).state();
        assertThat(out.player("p1").orElseThrow().bonus()).isEqualTo(10); // pirate ×3, fire ×2
        assertThat(out.player("p2").orElseThrow().bonus()).isZero();
        assertThat(out.log()).anyMatch(l -> l.contains("« pirate »"));
    }

    @Test
    void ignoresOtherEvents() {
        GameState s = new GameEngine().create("g", 1, new GameSettings(10, 1, 1, 2, 30),
                List.of(new SlotDef("s", "S")), List.of(card("a", "x")));
        assertThat(new TagSynergyRule().apply(new GameEvent.GameStarted(), s, RuleParams.EMPTY))
                .isEqualTo(RuleResult.ok(s));
    }
}
