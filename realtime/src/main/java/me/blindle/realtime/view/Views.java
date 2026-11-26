package me.blindle.realtime.view;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Messages envoyés aux clients. Chaque message porte {@code type} et {@code version}
 * (le client ignore une version plus ancienne que celle qu'il affiche).
 * Les dates sont des timestamps epoch en millisecondes (heure serveur).
 */
public final class Views {

    private Views() {
    }

    /** Vue publique d'une room, diffusée sur {@code /topic/room/{code}}. */
    public record RoomView(String type, String code, long version, String phase,
                           String presetId, String presetName, String themeName, String bidMode,
                           String verdictMode, List<String> rules, String hostId, int round, int totalRounds,
                           List<SlotView> slots, List<PlayerSummary> players, LotView lot,
                           Long deadline, Long serverTime, List<String> log, ResultView result,
                           int votesReceived, DailyView daily) {
    }

    /**
     * Défi du jour : date, puis en fin de partie rang obtenu et texte à partager (sans spoiler).
     */
    public record DailyView(String date, Integer rank, String share) {
    }

    public record SlotView(String id, String label, Set<String> requiredTags) {
    }

    public record PlayerSummary(String id, String name, int budget, int bonus, boolean host, boolean acted,
                                List<TeamEntry> team) {
    }

    /** @param paid prix payé (public : annoncé dans le journal) */
    public record TeamEntry(String slotId, CardView card, Integer paid) {
    }

    /** @param value valeur en pièces (verdict « value »), dérivée des stats et de la rareté publiques */
    public record CardView(String id, String name, Set<String> tags, String rarity, Map<String, Integer> stats,
                           int value) {
    }

    public record HintView(int level, String type, String value) {
    }

    public record BidView(String playerId, int amount) {
    }

    /**
     * Lot courant. {@code bids} est null tant que les offres sont secrètes ;
     * {@code cards} est null tant que la carte n'est pas révélée.
     */
    public record LotView(int hintLevel, int maxHintLevel, List<HintView> hints, int floorPrice,
                          int actedCount, List<BidView> bids, List<CardView> cards,
                          String winnerId, Integer price) {
    }

    public record ResultView(List<String> ranking, Map<String, Integer> scores) {
    }

    /** Vue privée d'un joueur, envoyée sur {@code /user/queue/room}. */
    public record PlayerView(String type, String code, long version, String playerId, String name,
                             boolean host, Integer myBid, boolean passed, boolean canBid,
                             boolean mustPlace, List<String> freeSlots, List<String> placeableSlots,
                             Map<String, Integer> jokers, List<HintView> privateHints, String votedFor) {
    }

    /** Erreur envoyée sur {@code /user/queue/errors}. */
    public record ErrorMessage(String type, String code, String message) {
        public static ErrorMessage of(String code, String message) {
            return new ErrorMessage("error", code, message);
        }
    }
}
