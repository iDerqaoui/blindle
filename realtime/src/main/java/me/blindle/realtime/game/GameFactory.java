package me.blindle.realtime.game;

import me.blindle.content.ContentRegistry;
import me.blindle.content.ContentValidationException;
import me.blindle.content.Preset;
import me.blindle.content.Theme;
import me.blindle.core.GameEngine;
import me.blindle.core.GameException;
import me.blindle.core.rule.RuleEngine;
import me.blindle.core.rule.RuleParams;
import me.blindle.modes.BidMode;
import me.blindle.modes.ModeCatalog;
import me.blindle.modes.VerdictMode;
import me.blindle.rules.RuleCatalog;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Assemble un {@link GameSetup} à partir d'un preset. Au démarrage, vérifie que chaque preset
 * référence des règles et des modes existants, compatibles avec son thème.
 */
@Component
public class GameFactory {

    private final ContentRegistry content;
    private final RuleCatalog rules;
    private final ModeCatalog modes;

    public GameFactory(ContentRegistry content, RuleCatalog rules, ModeCatalog modes) {
        this.content = content;
        this.rules = rules;
        this.modes = modes;
        validateAll();
    }

    private void validateAll() {
        List<String> errors = new ArrayList<>();
        for (Preset preset : content.presets()) {
            try {
                setup(preset.id());
            } catch (RuntimeException e) {
                errors.add("preset « " + preset.id() + " » : " + e.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            throw new ContentValidationException(errors);
        }
    }

    public GameSetup setup(String presetId) {
        Preset preset = content.preset(presetId)
                .orElseThrow(() -> new GameException("UNKNOWN_PRESET", "Preset inconnu : " + presetId));
        Theme theme = content.theme(preset.theme())
                .orElseThrow(() -> new IllegalStateException("Thème introuvable : " + preset.theme()));
        BidMode bidMode = modes.bidMode(preset.bidMode());
        VerdictMode verdictMode = modes.verdictMode(preset.verdictMode());
        for (String field : verdictMode.requiredThemeFields()) {
            if (!theme.allCardsHave(field)) {
                throw new IllegalArgumentException("le verdict « " + verdictMode.id()
                        + " » exige le champ « " + field + " » sur toutes les cartes");
            }
        }
        List<RuleEngine.ActiveRule> active = new ArrayList<>();
        for (Preset.RuleRef ref : preset.rules()) {
            active.add(new RuleEngine.ActiveRule(rules.require(ref.ref()), RuleParams.of(ref.params())));
        }
        return new GameSetup(preset, theme, new GameEngine(new RuleEngine(active)), bidMode, verdictMode);
    }

    public List<Preset> presets() {
        return content.presets();
    }
}
