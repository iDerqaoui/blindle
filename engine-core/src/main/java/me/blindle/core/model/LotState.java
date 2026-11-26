package me.blindle.core.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Lot en cours d'enchère.
 *
 * @param hintLevel  dernier palier d'indice révélé (0 = aucun)
 * @param floorPrice offre minimale du palier courant (fixée par les règles)
 * @param bids       offres du palier courant, dans l'ordre de réception (une par joueur)
 * @param passes     joueurs ayant passé sur le palier courant
 * @param deadline   heure de fin des enchères (null hors BIDDING)
 * @param winnerId   gagnant (null tant que non attribué)
 * @param price      prix payé par le gagnant
 */
public record LotState(List<Card> cards, int hintLevel, int floorPrice, List<Bid> bids,
                       List<String> passes, Instant deadline, String winnerId, int price) {

    public LotState {
        cards = List.copyOf(Objects.requireNonNull(cards, "cards"));
        bids = bids == null ? List.of() : List.copyOf(bids);
        passes = passes == null ? List.of() : List.copyOf(passes);
        if (cards.isEmpty()) {
            throw new IllegalArgumentException("Un lot contient au moins une carte");
        }
    }

    public static LotState of(List<Card> cards) {
        return new LotState(cards, 0, 0, List.of(), List.of(), null, null, 0);
    }

    /** Nombre de paliers disponibles : le plus petit max d'indices parmi les cartes du lot. */
    public int maxHintLevel() {
        return cards.stream().mapToInt(Card::maxHintLevel).min().orElse(0);
    }

    /** A misé ou passé sur le palier courant. */
    public boolean hasActed(String playerId) {
        return passes.contains(playerId) || bids.stream().anyMatch(b -> b.playerId().equals(playerId));
    }

    /** Nouveau palier : offres et passes du palier précédent sont effacées. */
    public LotState withHintLevel(int level) {
        return new LotState(cards, level, floorPrice, List.of(), List.of(), null, winnerId, price);
    }

    public LotState withFloorPrice(int floor) {
        return new LotState(cards, hintLevel, floor, bids, passes, deadline, winnerId, price);
    }

    public LotState withDeadline(Instant d) {
        return new LotState(cards, hintLevel, floorPrice, bids, passes, d, winnerId, price);
    }

    /** Ajoute l'offre du joueur, en remplaçant sa précédente offre ou sa passe. */
    public LotState withBid(Bid bid) {
        List<Bid> b = new ArrayList<>();
        for (Bid existing : bids) {
            if (!existing.playerId().equals(bid.playerId())) {
                b.add(existing);
            }
        }
        b.add(bid);
        List<String> p = new ArrayList<>(passes);
        p.remove(bid.playerId());
        return new LotState(cards, hintLevel, floorPrice, b, p, deadline, winnerId, price);
    }

    /** Le joueur passe : son éventuelle offre est retirée. */
    public LotState withPass(String playerId) {
        List<Bid> b = bids.stream().filter(x -> !x.playerId().equals(playerId)).toList();
        List<String> p = new ArrayList<>(passes);
        if (!p.contains(playerId)) {
            p.add(playerId);
        }
        return new LotState(cards, hintLevel, floorPrice, b, p, deadline, winnerId, price);
    }

    public LotState withWinner(String playerId, int paid) {
        return new LotState(cards, hintLevel, floorPrice, bids, passes, null, playerId, paid);
    }
}
