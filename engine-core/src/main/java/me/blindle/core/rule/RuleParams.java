package me.blindle.core.rule;

import java.util.HashMap;
import java.util.Map;

/**
 * Paramètres bruts d'une règle (issus du JSON du preset). Chaque règle les convertit
 * en record typé ; une valeur du mauvais type lève {@link IllegalArgumentException}.
 */
public record RuleParams(Map<String, Object> values) {

    public static final RuleParams EMPTY = new RuleParams(Map.of());

    public RuleParams {
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    /** Paramètres par défaut d'une règle, dans l'ordre donné : {@code defaults("base", 50, "step", 50)}. */
    public static Map<String, Object> defaults(Object... keyValues) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            out.put((String) keyValues[i], keyValues[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    public static RuleParams of(Map<String, ?> values) {
        return new RuleParams(new HashMap<>(values));
    }

    public int getInt(String name, int defaultValue) {
        Object v = values.get(name);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) {
            return n.intValue();
        }
        throw new IllegalArgumentException("Paramètre « " + name + " » : entier attendu, reçu " + v);
    }

    public double getDouble(String name, double defaultValue) {
        Object v = values.get(name);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        throw new IllegalArgumentException("Paramètre « " + name + " » : nombre attendu, reçu " + v);
    }

    public String getString(String name, String defaultValue) {
        Object v = values.get(name);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof String s) {
            return s;
        }
        throw new IllegalArgumentException("Paramètre « " + name + " » : texte attendu, reçu " + v);
    }
}
