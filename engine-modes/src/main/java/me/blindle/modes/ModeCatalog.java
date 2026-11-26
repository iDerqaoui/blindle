package me.blindle.modes;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Catalogue des modes d'enchère et de verdict disponibles, indexés par identifiant. */
@Component
public class ModeCatalog {

    private final Map<String, BidMode> bidModes;
    private final Map<String, VerdictMode> verdictModes;

    public ModeCatalog(List<BidMode> bidModes, List<VerdictMode> verdictModes) {
        this.bidModes = bidModes.stream()
                .collect(Collectors.toUnmodifiableMap(BidMode::id, Function.identity()));
        this.verdictModes = verdictModes.stream()
                .collect(Collectors.toUnmodifiableMap(VerdictMode::id, Function.identity()));
    }

    public BidMode bidMode(String id) {
        BidMode mode = bidModes.get(id);
        if (mode == null) {
            throw new IllegalArgumentException("Mode d'enchère inconnu : " + id);
        }
        return mode;
    }

    public VerdictMode verdictMode(String id) {
        VerdictMode mode = verdictModes.get(id);
        if (mode == null) {
            throw new IllegalArgumentException("Mode de verdict inconnu : " + id);
        }
        return mode;
    }
}
