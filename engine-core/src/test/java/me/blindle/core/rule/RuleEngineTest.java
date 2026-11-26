package me.blindle.core.rule;

import me.blindle.core.GameEngine;
import me.blindle.core.Fixtures;
import me.blindle.core.event.GameEvent;
import me.blindle.core.event.GameHook;
import me.blindle.core.model.GameState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleEngineTest {

    private final GameState state = Fixtures.lobby(new GameEngine(), 1, 3);
    private final List<String> calls = new ArrayList<>();

    /** Règle de test qui note son passage dans le journal et dans {@link #calls}. */
    private Rule rule(String type, int priority, GameHook hook, boolean reject) {
        return new Rule() {
            public String type() {
                return type;
            }

            public Set<GameHook> hooks() {
                return Set.of(hook);
            }

            public int priority() {
                return priority;
            }

            public RuleResult apply(GameEvent event, GameState s, RuleParams params) {
                calls.add(type);
                return reject ? RuleResult.reject("NO", "refus de " + type) : RuleResult.ok(s.withLog(type));
            }
        };
    }

    private static RuleEngine.ActiveRule active(Rule r) {
        return new RuleEngine.ActiveRule(r, RuleParams.EMPTY);
    }

    @Test
    void rulesRunByAscendingPriorityAndChainStates() {
        RuleEngine engine = new RuleEngine(List.of(
                active(rule("effect", 100, GameHook.ON_GAME_START, false)),
                active(rule("validation", 10, GameHook.ON_GAME_START, false)),
                active(rule("other-hook", 1, GameHook.ON_ROUND_END, false))));

        GameState out = engine.dispatch(new GameEvent.GameStarted(), state);

        assertThat(calls).containsExactly("validation", "effect");
        assertThat(out.log()).endsWith("validation", "effect");
    }

    @Test
    void equalPrioritiesKeepDeclarationOrder() {
        RuleEngine engine = new RuleEngine(List.of(
                active(rule("b", 100, GameHook.ON_GAME_START, false)),
                active(rule("a", 100, GameHook.ON_GAME_START, false))));

        engine.dispatch(new GameEvent.GameStarted(), state);

        assertThat(calls).containsExactly("b", "a");
    }

    @Test
    void rejectionStopsTheChain() {
        RuleEngine engine = new RuleEngine(List.of(
                active(rule("guard", 10, GameHook.ON_GAME_START, true)),
                active(rule("effect", 100, GameHook.ON_GAME_START, false))));

        assertThatThrownBy(() -> engine.dispatch(new GameEvent.GameStarted(), state))
                .isInstanceOf(RuleRejectedException.class)
                .hasMessage("refus de guard")
                .hasFieldOrPropertyWithValue("ruleType", "guard");
        assertThat(calls).containsExactly("guard");
    }

    @Test
    void typedParamsAreChecked() {
        RuleParams params = RuleParams.of(java.util.Map.of("base", 50, "rate", 0.1, "bad", "x"));

        assertThat(params.getInt("base", 0)).isEqualTo(50);
        assertThat(params.getInt("missing", 7)).isEqualTo(7);
        assertThat(params.getDouble("rate", 0)).isEqualTo(0.1);
        assertThatThrownBy(() -> params.getInt("bad", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> params.getInt("rate", 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
