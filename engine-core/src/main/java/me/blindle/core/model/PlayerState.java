package me.blindle.core.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @param team         slotId → cardId
 * @param paid         cardId → prix payé pour cette carte
 * @param jokers       jokerId → utilisations restantes
 * @param privateHints indices vus par ce joueur seul (ex. joker espion), effacés en fin de round
 * @param bonus        points ajoutés par les règles (synergies…), utilisés par le verdict
 */
public record PlayerState(String id, String name, int budget, Map<String, String> team,
                          Map<String, Integer> paid, Map<String, Integer> jokers,
                          List<Hint> privateHints, int bonus, boolean bot) {

    public PlayerState {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        team = team == null ? Map.of() : Map.copyOf(team);
        paid = paid == null ? Map.of() : Map.copyOf(paid);
        jokers = jokers == null ? Map.of() : Map.copyOf(jokers);
        privateHints = privateHints == null ? List.of() : List.copyOf(privateHints);
    }

    public static PlayerState join(String id, String name, boolean bot) {
        return new PlayerState(id, name, 0, Map.of(), Map.of(), Map.of(), List.of(), 0, bot);
    }

    public int jokersLeft(String jokerId) {
        return jokers.getOrDefault(jokerId, 0);
    }

    /** Slot occupé par la carte, ou null. */
    public String slotOf(String cardId) {
        return team.entrySet().stream().filter(e -> e.getValue().equals(cardId))
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }

    public PlayerState withBudget(int newBudget) {
        return new PlayerState(id, name, newBudget, team, paid, jokers, privateHints, bonus, bot);
    }

    public PlayerState withCardInSlot(String slotId, String cardId) {
        Map<String, String> t = new HashMap<>(team);
        t.put(slotId, cardId);
        return new PlayerState(id, name, budget, t, paid, jokers, privateHints, bonus, bot);
    }

    public PlayerState withCardPaid(String cardId, int price) {
        Map<String, Integer> p = new HashMap<>(paid);
        p.put(cardId, price);
        return new PlayerState(id, name, budget, team, p, jokers, privateHints, bonus, bot);
    }

    /** Retire la carte du slot (et son prix payé). */
    public PlayerState withoutSlot(String slotId) {
        Map<String, String> t = new HashMap<>(team);
        String cardId = t.remove(slotId);
        Map<String, Integer> p = new HashMap<>(paid);
        if (cardId != null) {
            p.remove(cardId);
        }
        return new PlayerState(id, name, budget, t, p, jokers, privateHints, bonus, bot);
    }

    public PlayerState withJokers(String jokerId, int uses) {
        Map<String, Integer> j = new HashMap<>(jokers);
        j.put(jokerId, uses);
        return new PlayerState(id, name, budget, team, paid, j, privateHints, bonus, bot);
    }

    public PlayerState withPrivateHint(Hint hint) {
        List<Hint> h = new ArrayList<>(privateHints);
        h.add(hint);
        return new PlayerState(id, name, budget, team, paid, jokers, h, bonus, bot);
    }

    public PlayerState withoutPrivateHints() {
        return new PlayerState(id, name, budget, team, paid, jokers, List.of(), bonus, bot);
    }

    public PlayerState withBonus(int newBonus) {
        return new PlayerState(id, name, budget, team, paid, jokers, privateHints, newBonus, bot);
    }
}
