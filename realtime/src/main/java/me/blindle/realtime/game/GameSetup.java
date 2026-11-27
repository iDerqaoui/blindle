package me.blindle.realtime.game;

import me.blindle.content.Preset;
import me.blindle.content.Theme;
import me.blindle.core.GameEngine;
import me.blindle.core.model.Card;
import me.blindle.modes.BidMode;
import me.blindle.modes.VerdictMode;

import java.util.List;
import java.util.Set;

/**
 * Tout ce qu'il faut pour jouer un preset : contenu, moteur avec ses règles, modes.
 *
 * @param deck        cartes jouables (les pièges n'y sont que si la règle trap-lots est active)
 * @param rules       identifiants des règles actives
 * @param activeRules règles actives avec leurs paramètres (celles du preset, ou celles choisies par l'hôte)
 * @param overrides   personnalisation de la room par son hôte ({@link RoomOverrides#NONE} sinon)
 */
public record GameSetup(Preset preset, Theme theme, GameEngine engine, BidMode bidMode,
                        VerdictMode verdictMode, List<Card> deck, Set<String> rules,
                        List<Preset.RuleRef> activeRules, RoomOverrides overrides) {
}
