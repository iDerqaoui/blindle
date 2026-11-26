package me.blindle.core.model;

import me.blindle.core.Phase;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Snapshot immuable d'une partie. Toute modification renvoie un nouvel état.
 *
 * @param deck       cartes restantes, dans l'ordre de tirage (mélangé par la seed au démarrage)
 * @param roundIndex nombre de lots tirés (1 pendant le premier round)
 * @param lot        lot courant (null hors round)
 * @param discarded  cartes invendues
 * @param log        journal lisible de la partie
 * @param result     résultat final (null avant FINISHED)
 * @param version    incrémentée à chaque action, sert au client à ignorer les messages obsolètes
 */
public record GameState(String gameId, long seed, Phase phase, GameSettings settings,
                        List<SlotDef> slots, List<PlayerState> players, List<Card> deck,
                        int roundIndex, LotState lot, List<Card> discarded, List<String> log,
                        GameResult result, long version) {

    public GameState {
        Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(settings, "settings");
        slots = List.copyOf(slots);
        players = List.copyOf(players);
        deck = List.copyOf(deck);
        discarded = discarded == null ? List.of() : List.copyOf(discarded);
        log = log == null ? List.of() : List.copyOf(log);
    }

    public Optional<PlayerState> player(String playerId) {
        return players.stream().filter(p -> p.id().equals(playerId)).findFirst();
    }

    public Optional<SlotDef> slot(String slotId) {
        return slots.stream().filter(s -> s.id().equals(slotId)).findFirst();
    }

    /** Nombre de slots encore libres dans l'équipe du joueur. */
    public int freeSlots(String playerId) {
        return player(playerId).map(p -> slots.size() - p.team().size()).orElse(0);
    }

    public GameState withPhase(Phase p) {
        return new GameState(gameId, seed, p, settings, slots, players, deck, roundIndex, lot,
                discarded, log, result, version);
    }

    public GameState withSettings(GameSettings s) {
        return new GameState(gameId, seed, phase, s, slots, players, deck, roundIndex, lot,
                discarded, log, result, version);
    }

    public GameState withPlayers(List<PlayerState> ps) {
        return new GameState(gameId, seed, phase, settings, slots, ps, deck, roundIndex, lot,
                discarded, log, result, version);
    }

    public GameState withPlayer(String playerId, UnaryOperator<PlayerState> change) {
        List<PlayerState> ps = new ArrayList<>();
        boolean found = false;
        for (PlayerState p : players) {
            if (p.id().equals(playerId)) {
                ps.add(change.apply(p));
                found = true;
            } else {
                ps.add(p);
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Joueur inconnu : " + playerId);
        }
        return withPlayers(ps);
    }

    public GameState withDeck(List<Card> d) {
        return new GameState(gameId, seed, phase, settings, slots, players, d, roundIndex, lot,
                discarded, log, result, version);
    }

    public GameState withRoundIndex(int r) {
        return new GameState(gameId, seed, phase, settings, slots, players, deck, r, lot,
                discarded, log, result, version);
    }

    public GameState withLot(LotState l) {
        return new GameState(gameId, seed, phase, settings, slots, players, deck, roundIndex, l,
                discarded, log, result, version);
    }

    public GameState withDiscarded(List<Card> d) {
        return new GameState(gameId, seed, phase, settings, slots, players, deck, roundIndex, lot,
                d, log, result, version);
    }

    public GameState withLog(String message) {
        List<String> l = new ArrayList<>(log);
        l.add(message);
        return new GameState(gameId, seed, phase, settings, slots, players, deck, roundIndex, lot,
                discarded, l, result, version);
    }

    public GameState withResult(GameResult r) {
        return new GameState(gameId, seed, phase, settings, slots, players, deck, roundIndex, lot,
                discarded, log, r, version);
    }

    public GameState nextVersion() {
        return new GameState(gameId, seed, phase, settings, slots, players, deck, roundIndex, lot,
                discarded, log, result, version + 1);
    }
}
