package me.blindle.core.model;

/** Paramètres d'une partie (issus du preset, ajustables par l'hôte en lobby). */
public record GameSettings(int budget, int rounds, int teamSize, int hintLevels, int bidTimerSec) {

    public GameSettings {
        if (budget <= 0 || rounds <= 0 || teamSize <= 0 || hintLevels < 1 || bidTimerSec <= 0) {
            throw new IllegalArgumentException("Paramètres de partie invalides");
        }
    }
}
