package me.blindle.core;

/**
 * Phases d'une partie. Un round enchaîne DRAW → HINT → BIDDING → AWARDED → REVEALED → SLOT.
 */
public enum Phase {
    LOBBY,
    STARTED,
    DRAW,
    HINT,
    BIDDING,
    AWARDED,
    REVEALED,
    SLOT,
    VERDICT,
    FINISHED
}
