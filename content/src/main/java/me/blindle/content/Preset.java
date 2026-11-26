package me.blindle.content;

import me.blindle.core.model.GameSettings;

import java.util.List;
import java.util.Map;

/** Preset : thème + règles + mode d'enchère + mode de verdict + paramètres (§6). */
public record Preset(String id, String name, String description, String theme, String bidMode,
                     String verdictMode, List<String> channels, Settings settings, List<RuleRef> rules) {

    public Preset {
        channels = channels == null ? List.of() : List.copyOf(channels);
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public record Settings(int budget, int rounds, int teamSize, int hintLevels, int bidTimerSec) {
        public GameSettings toGameSettings() {
            return new GameSettings(budget, rounds, teamSize, hintLevels, bidTimerSec);
        }
    }

    /** Référence à une règle du catalogue, avec ses paramètres. */
    public record RuleRef(String ref, Map<String, Object> params) {
        public RuleRef {
            params = params == null ? Map.of() : Map.copyOf(params);
        }
    }
}
