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
                           int votesReceived, DailyView daily, String drawerId, List<CardView> trash,
                           ArtView art, boolean customized, int deckSize,
                           Map<String, Map<String, Object>> ruleParams, boolean publicRoom) {
    }

    /**
     * Ce que l'hôte peut personnaliser dans sa room, envoyé à lui seul sur {@code /user/queue/catalog}.
     *
     * @param rarities raretés existantes du thème (seules valeurs possibles pour une carte)
     */
    public record RoomCatalogView(String type, String code, List<CatalogCard> cards, List<String> rarities,
                                  List<CatalogRule> rules) {
    }

    /** @param baseRarity rareté fixée par l'administrateur ; {@code rarity} celle de la room */
    public record CatalogCard(String id, String name, String image, String baseRarity, String rarity,
                              boolean trap, boolean included) {
    }

    /** @param params paramètres actifs (ou par défaut si la règle est inactive) */
    public record CatalogRule(String type, boolean active, Map<String, Object> params,
                              Map<String, Object> defaults, Set<String> requiredBidModes, Set<String> conflictsWith) {
    }

    /**
     * Défi du jour : date, puis en fin de partie rang obtenu et texte à partager (sans spoiler).
     */
    public record DailyView(String date, Integer rank, String share) {
    }

    /** Habillage de l'univers : affiche, bannière, couleur (dos de carte). */
    public record ArtView(String cover, String banner, String color) {
    }

    public record SlotView(String id, String label, Set<String> requiredTags) {
    }

    /** @param botProfile profil du bot (aggressive, stingy…), null pour un humain : le client traduit le nom */
    public record PlayerSummary(String id, String name, int budget, int bonus, boolean host, boolean bot, String botProfile,
                                boolean acted, List<TeamEntry> team, boolean connected) {
    }

    /** @param paid prix payé (public : annoncé dans le journal) */
    public record TeamEntry(String slotId, CardView card, Integer paid) {
    }

    /** @param value valeur en pièces (verdict « value »), dérivée des stats et de la rareté publiques */
    public record CardView(String id, String name, Set<String> tags, String rarity, Map<String, Integer> stats,
                           int value, int points, String image) {
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
                             Map<String, Integer> jokers, List<HintView> privateHints, String votedFor,
                             CardView drawnCard) {
    }

    /** Erreur envoyée sur {@code /user/queue/errors}. */
    public record ErrorMessage(String type, String code, String message) {
        public static ErrorMessage of(String code, String message) {
            return new ErrorMessage("error", code, message);
        }
    }
}
