package me.blindle.core.model;

import me.blindle.core.Phase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Snapshot immuable d'une partie. Toute modification renvoie un nouvel état.
 *
 * @param cards      toutes les cartes de la partie, par identifiant (index immuable)
 * @param deck       cartes restantes, dans l'ordre de tirage (mélangé par la seed au démarrage)
 * @param roundIndex nombre de lots tirés (1 pendant le premier round)
 * @param lot        lot courant (null hors round)
 * @param discarded  cartes invendues ou défaussées
 * @param log        journal lisible de la partie
 * @param result     résultat final (null avant FINISHED)
 * @param version    incrémentée à chaque action, sert au client à ignorer les messages obsolètes
 */
public record GameState(String gameId, long seed, Phase phase, GameSettings settings,
                        List<SlotDef> slots, List<PlayerState> players, Map<String, Card> cards,
                        List<Card> deck, int roundIndex, LotState lot, List<Card> discarded,
                        List<String> log, GameResult result, long version) {

    public GameState {
        Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(settings, "settings");
        slots = List.copyOf(slots);
        players = List.copyOf(players);
        cards = Map.copyOf(cards);
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

    public Optional<Card> card(String cardId) {
        return Optional.ofNullable(cards.get(cardId));
    }

    /** Nombre de slots encore libres dans l'équipe du joueur. */
    public int freeSlots(String playerId) {
        return player(playerId).map(p -> slots.size() - p.team().size()).orElse(0);
    }

    /** Joueurs autorisés à miser sur le lot courant : assez de slots libres pour ses cartes. */
    public List<String> eligibleBidders() {
        int needed = lot == null ? 1 : lot.cards().size();
        return players.stream().map(PlayerState::id).filter(id -> freeSlots(id) >= needed).toList();
    }

    private GameState copy(Phase p, GameSettings s, List<PlayerState> ps, List<Card> d, int r,
                           LotState l, List<Card> disc, List<String> lg, GameResult res, long v) {
        return new GameState(gameId, seed, p, s, slots, ps, cards, d, r, l, disc, lg, res, v);
    }

    public GameState withPhase(Phase p) {
        return copy(p, settings, players, deck, roundIndex, lot, discarded, log, result, version);
    }

    public GameState withSettings(GameSettings s) {
        return copy(phase, s, players, deck, roundIndex, lot, discarded, log, result, version);
    }

    public GameState withPlayers(List<PlayerState> ps) {
        return copy(phase, settings, ps, deck, roundIndex, lot, discarded, log, result, version);
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

    public GameState withEachPlayer(UnaryOperator<PlayerState> change) {
        return withPlayers(players.stream().map(change).toList());
    }

    public GameState withDeck(List<Card> d) {
        return copy(phase, settings, players, d, roundIndex, lot, discarded, log, result, version);
    }

    public GameState withRoundIndex(int r) {
        return copy(phase, settings, players, deck, r, lot, discarded, log, result, version);
    }

    public GameState withLot(LotState l) {
        return copy(phase, settings, players, deck, roundIndex, l, discarded, log, result, version);
    }

    public GameState withDiscarded(List<Card> d) {
        return copy(phase, settings, players, deck, roundIndex, lot, d, log, result, version);
    }

    public GameState withLog(String message) {
        List<String> l = new ArrayList<>(log);
        l.add(message);
        return copy(phase, settings, players, deck, roundIndex, lot, discarded, l, result, version);
    }

    public GameState withResult(GameResult r) {
        return copy(phase, settings, players, deck, roundIndex, lot, discarded, log, r, version);
    }

    public GameState nextVersion() {
        return copy(phase, settings, players, deck, roundIndex, lot, discarded, log, result, version + 1);
    }
}
