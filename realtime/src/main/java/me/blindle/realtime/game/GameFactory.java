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
import me.blindle.modes.ValueVerdictMode;
import me.blindle.modes.VerdictMode;
import me.blindle.rules.RuleCatalog;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
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
        return setup(presetId, RoomOverrides.NONE);
    }

    /**
     * Setup d'une room personnalisée par son hôte : mêmes contrôles qu'un preset (requires,
     * conflictsWith, paramètres). Une personnalisation invalide lève {@link GameException}.
     */
    public GameSetup customized(String presetId, RoomOverrides overrides) {
        try {
            return setup(presetId, overrides);
        } catch (GameException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new GameException("BAD_ROOM_SETUP", e.getMessage());
        }
    }

    /** Règles que l'hôte peut activer, avec leurs paramètres par défaut. */
    public List<Rule> catalog() {
        return rules.all().values().stream().sorted(java.util.Comparator.comparing(Rule::type)).toList();
    }

    private GameSetup setup(String presetId, RoomOverrides overrides) {
        Preset preset = content.preset(presetId)
                .orElseThrow(() -> new GameException("UNKNOWN_PRESET", "Preset inconnu : " + presetId));
        Theme theme = content.theme(preset.theme())
                .orElseThrow(() -> new IllegalStateException("Thème introuvable : " + preset.theme()));
        BidMode bidMode = modes.bidMode(preset.bidMode());
        VerdictMode verdictMode = modes.verdictMode(preset.verdictMode());
        if (preset.daily() && !ValueVerdictMode.ID.equals(verdictMode.id())) {
            throw new IllegalArgumentException("un défi du jour se joue avec le verdict « value »");
        }
        if (!verdictMode.supportsTheme(theme::allCardsHave)) {
            throw new IllegalArgumentException("le verdict « " + verdictMode.id() + " » exige "
                    + verdictMode.themeRequirement());
        }
        List<RuleEngine.ActiveRule> active = new ArrayList<>();
        Set<String> refs = new LinkedHashSet<>();
        List<Preset.RuleRef> ruleRefs = overrides.rules() == null ? preset.rules() : overrides.rules();
        ruleRefs.forEach(r -> {
            if (!refs.add(r.ref())) {
                throw new IllegalArgumentException("règle « " + r.ref() + " » en double");
            }
        });
        List<Preset.RuleRef> complete = new ArrayList<>();
        for (Preset.RuleRef ref : ruleRefs) {
            Rule rule = rules.require(ref.ref());
            // Paramètres complets : défauts de la règle, puis ceux du preset ou de l'hôte.
            Map<String, Object> merged = new LinkedHashMap<>(rule.defaultParams());
            merged.putAll(ref.params());
            RuleParams params = RuleParams.of(merged);
            checkCompatibility(rule, params, theme, bidMode, refs);
            active.add(new RuleEngine.ActiveRule(rule, params));
            complete.add(new Preset.RuleRef(ref.ref(), merged));
        }
        Set<String> rarities = new LinkedHashSet<>();
        theme.cards().forEach(c -> {
            if (c.rarity() != null) {
                rarities.add(c.rarity());
            }
        });
        overrides.rarities().forEach((cardId, rarity) -> {
            if (theme.cards().stream().noneMatch(c -> c.id().equals(cardId))) {
                throw new IllegalArgumentException("carte inconnue : " + cardId);
            }
            if (!rarities.contains(rarity)) {
                throw new IllegalArgumentException("rareté « " + rarity + " » absente du thème");
            }
        });
        List<Card> deck = theme.toCards().stream()
                .filter(c -> !c.trap() || refs.contains("trap-lots"))
                .filter(c -> !overrides.excludedCards().contains(c.id()))
                .map(c -> withRarity(c, overrides.rarities().get(c.id())))
                .toList();
        // Une room personnalisée ajuste ensuite ses manches au paquet ; il lui faut au moins une carte.
        int needed = overrides.isEmpty() ? preset.settings().rounds() : 1;
        if (deck.size() < needed) {
            throw new IllegalArgumentException(preset.settings().rounds() + " rounds pour " + deck.size()
                    + " cartes jouables");
        }
        return new GameSetup(preset, theme, new GameEngine(new RuleEngine(active)), bidMode, verdictMode,
                deck, Set.copyOf(refs), complete, overrides);
    }

    private static Card withRarity(Card c, String rarity) {
        return rarity == null ? c : new Card(c.id(), c.name(), c.tags(), c.stats(), rarity, c.trap(), c.hints());
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
