package me.blindle.content;

import me.blindle.core.model.GameSettings;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Preset : thème + règles + mode d'enchère + mode de verdict + paramètres (§6).
 * {@code daily} : preset de défi du jour, joué seul avec une seed par jour (§7).
 */
public record Preset(String id, String name, String description, String theme, String bidMode,
                     String verdictMode, List<String> channels, boolean daily, Settings settings,
                     List<RuleRef> rules) {

    public Preset {
        channels = channels == null ? List.of() : List.copyOf(channels);
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    /**
     * Canaux où le preset se joue : ceux qu'il déclare, sinon ceux de son thème. Le chargeur garantit
     * qu'un preset ne fait que restreindre les canaux de son thème.
     */
    public Set<Channel> channels(Theme theme) {
        List<String> ids = channels.isEmpty() ? theme.rights().channels() : channels;
        Set<Channel> out = EnumSet.noneOf(Channel.class);
        ids.forEach(id -> Channel.of(id).ifPresent(out::add));
        return out;
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
