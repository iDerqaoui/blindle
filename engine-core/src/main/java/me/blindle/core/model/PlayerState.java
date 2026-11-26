package me.blindle.core.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @param team slotId → cardId
 */
public record PlayerState(String id, String name, int budget, Map<String, String> team, boolean bot) {

    public PlayerState {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        team = team == null ? Map.of() : Map.copyOf(team);
    }

    public static PlayerState join(String id, String name, boolean bot) {
        return new PlayerState(id, name, 0, Map.of(), bot);
    }

    public PlayerState withBudget(int newBudget) {
        return new PlayerState(id, name, newBudget, team, bot);
    }

    public PlayerState withCardInSlot(String slotId, String cardId) {
        Map<String, String> t = new HashMap<>(team);
        t.put(slotId, cardId);
        return new PlayerState(id, name, budget, t, bot);
    }
}
