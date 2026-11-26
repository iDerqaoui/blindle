package me.blindle.core;

public class IllegalPhaseTransitionException extends GameException {

    public IllegalPhaseTransitionException(Phase from, Phase to) {
        super("ILLEGAL_TRANSITION", "Transition interdite : " + from + " → " + to);
    }
}
