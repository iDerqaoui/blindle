package me.blindle.core.event;

import me.blindle.core.model.Bid;

import java.util.List;

/**
 * Événement émis par le moteur. Chaque événement déclenche les règles de son {@link GameHook}.
 */
public sealed interface GameEvent {

    GameHook hook();

    record GameStarted() implements GameEvent {
        public GameHook hook() {
            return GameHook.ON_GAME_START;
        }
    }

    record RoundStarted(int round, List<String> cardIds) implements GameEvent {
        public RoundStarted {
            cardIds = List.copyOf(cardIds);
        }

        public GameHook hook() {
            return GameHook.ON_ROUND_START;
        }
    }

    record HintRevealed(int round, int level) implements GameEvent {
        public GameHook hook() {
            return GameHook.ON_HINT_REVEAL;
        }
    }

    /** @param at heure serveur de réception (null si inconnue, ex. en simulation) */
    record BidPlaced(Bid bid, java.time.Instant at) implements GameEvent {
        public BidPlaced(Bid bid) {
            this(bid, null);
        }

        public GameHook hook() {
            return GameHook.ON_BID_PLACED;
        }
    }

    /** @param sellerId vendeur du lot (ex. le piocheur), null si la banque vend */
    record LotWon(String playerId, int price, String sellerId) implements GameEvent {
        public LotWon(String playerId, int price) {
            this(playerId, price, null);
        }

        public GameHook hook() {
            return GameHook.ON_LOT_WON;
        }
    }

    /** Lot invendu, parti à la défausse. @param sellerId vendeur du lot, null si la banque vend */
    record LotDiscarded(int round, String sellerId) implements GameEvent {
        public GameHook hook() {
            return GameHook.ON_LOT_DISCARDED;
        }
    }

    record CardRevealed(String winnerId, List<String> cardIds) implements GameEvent {
        public CardRevealed {
            cardIds = List.copyOf(cardIds);
        }

        public GameHook hook() {
            return GameHook.ON_CARD_REVEALED;
        }
    }

    record SlotAssigned(String playerId, String slotId, String cardId) implements GameEvent {
        public GameHook hook() {
            return GameHook.ON_SLOT_ASSIGNED;
        }
    }

    record JokerUsed(String playerId, String jokerId, String targetId) implements GameEvent {
        public GameHook hook() {
            return GameHook.ON_JOKER_USED;
        }
    }

    record RoundEnded(int round, boolean sold) implements GameEvent {
        public GameHook hook() {
            return GameHook.ON_ROUND_END;
        }
    }

    record VerdictRequested() implements GameEvent {
        public GameHook hook() {
            return GameHook.ON_VERDICT;
        }
    }
}
