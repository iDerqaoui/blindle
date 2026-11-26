package me.blindle.core.rule;

import me.blindle.core.GameException;

/** Action rejetée par une règle ; le message est renvoyé au joueur. */
public class RuleRejectedException extends GameException {

    private final String ruleType;

    public RuleRejectedException(String ruleType, String code, String message) {
        super(code, message);
        this.ruleType = ruleType;
    }

    public String ruleType() {
        return ruleType;
    }
}
