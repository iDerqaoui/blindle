package me.blindle.rules;

import me.blindle.core.event.GameEvent;
import me.blindle.core.event.GameHook;
import me.blindle.core.model.Card;
import me.blindle.core.model.GameState;
import me.blindle.core.model.SlotDef;
import me.blindle.core.rule.Rule;
import me.blindle.core.rule.RuleParams;
import me.blindle.core.rule.RuleResult;
import org.springframework.stereotype.Component;

import java.util.Set;

/** {@code slot-constraint} : refuse une carte dans un slot dont elle n'a pas les tags requis. */
@Component
public class SlotConstraintRule implements Rule {

    public static final String TYPE = "slot-constraint";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Set<GameHook> hooks() {
        return Set.of(GameHook.ON_SLOT_ASSIGNED);
    }

    @Override
    public int priority() {
        return VALIDATION;
    }

    @Override
    public Set<String> requiredThemeFields() {
        return Set.of("slotTags");
    }

    @Override
    public RuleResult apply(GameEvent event, GameState state, RuleParams params) {
        if (!(event instanceof GameEvent.SlotAssigned assigned)) {
            return RuleResult.ok(state);
        }
        SlotDef slot = state.slot(assigned.slotId()).orElseThrow();
        Card card = state.card(assigned.cardId()).orElseThrow();
        if (!card.tags().containsAll(slot.requiredTags())) {
            return RuleResult.reject("SLOT_CONSTRAINT",
                    "« " + slot.label() + " » exige : " + String.join(", ", slot.requiredTags()));
        }
        return RuleResult.ok(state);
    }
}
