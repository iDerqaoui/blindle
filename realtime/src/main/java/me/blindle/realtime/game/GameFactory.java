package me.blindle.realtime.game;

import me.blindle.content.ContentRegistry;
import me.blindle.content.ContentValidationException;
import me.blindle.content.Preset;
import me.blindle.content.Theme;
import me.blindle.core.GameEngine;
import me.blindle.core.GameException;
import me.blindle.core.model.Card;
import me.blindle.core.rule.Rule;
import me.blindle.core.rule.RuleEngine;
import me.blindle.core.rule.RuleParams;
import me.blindle.modes.BidMode;
import me.blindle.modes.ModeCatalog;
import me.blindle.modes.VerdictMode;
import me.blindle.rules.RuleCatalog;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
        Set<String> refs = new LinkedHashSet<>();
        preset.rules().forEach(r -> refs.add(r.ref()));
        for (Preset.RuleRef ref : preset.rules()) {
            Rule rule = rules.require(ref.ref());
            RuleParams params = RuleParams.of(ref.params());
            checkCompatibility(rule, params, theme, bidMode, refs);
            active.add(new RuleEngine.ActiveRule(rule, params));
        }
        List<Card> deck = theme.toCards().stream()
                .filter(c -> !c.trap() || refs.contains("trap-lots"))
                .toList();
        if (deck.size() < preset.settings().rounds()) {
            throw new IllegalArgumentException(preset.settings().rounds() + " rounds pour " + deck.size()
                    + " cartes jouables");
        }
        return new GameSetup(preset, theme, new GameEngine(new RuleEngine(active)), bidMode, verdictMode,
                deck, Set.copyOf(refs));
    }

    /** Vérifie requires / conflictsWith / paramètres d'une règle (docs/PROJECT.md §5). */
    private static void checkCompatibility(Rule rule, RuleParams params, Theme theme, BidMode bidMode,
                                           Set<String> activeRefs) {
        for (String field : rule.requiredThemeFields()) {
            boolean ok = "slotTags".equals(field)
                    ? theme.defaultSlots().stream().anyMatch(s -> s.requiredTags() != null && !s.requiredTags().isEmpty())
                    : theme.allCardsHave(field);
            if (!ok) {
                throw new IllegalArgumentException("la règle « " + rule.type() + " » exige « " + field
                        + " » dans le thème « " + theme.id() + " »");
            }
        }
        if (!rule.requiredBidModes().isEmpty() && !rule.requiredBidModes().contains(bidMode.id())) {
            throw new IllegalArgumentException("la règle « " + rule.type() + " » exige le mode d'enchère "
                    + rule.requiredBidModes());
        }
        for (String conflict : rule.conflictsWith()) {
            if (activeRefs.contains(conflict)) {
                throw new IllegalArgumentException("« " + rule.type() + " » est incompatible avec « " + conflict + " »");
            }
        }
        try {
            rule.validateParams(params);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("paramètres invalides : " + e.getMessage());
        }
    }

    public List<Preset> presets() {
        return content.presets();
    }
}
