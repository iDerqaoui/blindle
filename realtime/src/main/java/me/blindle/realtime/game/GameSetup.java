package me.blindle.realtime.game;

import me.blindle.content.Preset;
import me.blindle.content.Theme;
import me.blindle.core.GameEngine;
import me.blindle.modes.BidMode;
import me.blindle.modes.VerdictMode;

/** Tout ce qu'il faut pour jouer un preset : contenu, moteur avec ses règles, modes. */
public record GameSetup(Preset preset, Theme theme, GameEngine engine, BidMode bidMode, VerdictMode verdictMode) {
}
