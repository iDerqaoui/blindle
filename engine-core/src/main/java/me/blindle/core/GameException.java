package me.blindle.core;

/**
 * Action refusée par le moteur. {@code code} est stable (renvoyé au client), le message est lisible.
 */
public class GameException extends RuntimeException {

    private final String code;

    public GameException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
