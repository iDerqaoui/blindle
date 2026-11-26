package me.blindle.core.model;

import java.util.Objects;
import java.util.Set;

/** Emplacement d'équipe, avec tags requis optionnels. */
public record SlotDef(String id, String label, Set<String> requiredTags) {

    public SlotDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        requiredTags = requiredTags == null ? Set.of() : Set.copyOf(requiredTags);
    }

    public SlotDef(String id, String label) {
        this(id, label, Set.of());
    }
}
