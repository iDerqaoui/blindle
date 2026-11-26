package me.blindle.core.rule;

import me.blindle.core.model.GameState;

import java.util.Objects;

/** Issue d'une règle : nouvel état, ou rejet de l'action en cours. */
public sealed interface RuleResult {

    static RuleResult ok(GameState state) {
        return new Continue(state);
    }

    static RuleResult reject(String code, String message) {
        return new Reject(code, message);
    }

    record Continue(GameState state) implements RuleResult {
        public Continue {
            Objects.requireNonNull(state, "state");
        }
    }

    record Reject(String code, String message) implements RuleResult {
        public Reject {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }
}
