package me.blindle.rules;

import me.blindle.core.Fixtures;
import me.blindle.core.GameEngine;
import me.blindle.core.model.Bid;
import me.blindle.core.model.Card;
import me.blindle.core.model.GameState;
import me.blindle.core.model.SlotDef;
import me.blindle.core.rule.RuleRejectedException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlotConstraintRuleTest {

    private final GameEngine engine = RulesSupport.engine(new SlotConstraintRule(), Map.of());

    /** Lot révélé, gagné par p1. Slot « support » exige le tag « support ». */
    private GameState revealed() {
        List<SlotDef> slots = List.of(new SlotDef("captain", "Capitaine"),
                new SlotDef("support", "Soutien", Set.of("support")));
        GameState s = engine.create("g", 1, Fixtures.settings(3), slots, Fixtures.deck(5));
        for (String id : List.of("p1", "p2", "p3")) {
            s = engine.addPlayer(s, id, id, false).state();
        }
        s = engine.start(s).state();
        s = engine.openBidding(engine.revealFirstHint(engine.nextRound(s).state()).state(), RulesSupport.DEADLINE).state();
        s = engine.placeBid(s, new Bid("p1", 100)).state();
        return engine.revealCard(engine.closeBidding(s, RulesSupport.HIGHEST).state()).state();
    }

    @Test
    void cardWithoutRequiredTagIsRefused() {
        GameState s = revealed();
        Card card = s.lot().cards().get(0);

        assertThatThrownBy(() -> engine.assignSlot(s, "p1", "support", card.id()))
                .isInstanceOf(RuleRejectedException.class)
                .hasFieldOrPropertyWithValue("code", "SLOT_CONSTRAINT");
        assertThat(engine.canAssign(s, "p1", "support", card.id())).isFalse();
        assertThat(engine.canAssign(s, "p1", "captain", card.id())).isTrue();
    }

    @Test
    void cardWithRequiredTagIsAccepted() {
        GameState s = revealed();
        Card original = s.lot().cards().get(0);
        Card tagged = new Card(original.id(), original.name(), Set.of("support"), original.stats(),
                original.rarity(), false, original.hints());
        Map<String, Card> index = new java.util.HashMap<>(s.cards());
        index.put(tagged.id(), tagged);
        GameState withTag = new GameState(s.gameId(), s.seed(), s.phase(), s.settings(), s.slots(), s.players(),
                index, s.deck(), s.roundIndex(), s.lot(), s.discarded(), s.log(), s.result(), s.version());

        assertThat(engine.assignSlot(withTag, "p1", "support", tagged.id()).state()
                .player("p1").orElseThrow().team()).containsEntry("support", tagged.id());
    }

    @Test
    void forfeitDiscardsUnplaceableCard() {
        GameState s = revealed();
        GameState after = engine.forfeit(s, "p1").state();
        assertThat(after.discarded()).containsExactly(s.lot().cards().get(0));
        assertThat(after.player("p1").orElseThrow().budget()).isEqualTo(900);
    }
}
