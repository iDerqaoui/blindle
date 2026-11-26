package me.blindle.modes.bot;

import me.blindle.core.model.Bid;
import me.blindle.core.model.Card;
import me.blindle.core.model.Hint;

import java.util.List;
import java.util.Optional;

/**
 * Ce qu'un bot voit au moment de décider, filtré comme la vue d'un humain : indices révélés
 * (jamais l'identité des cartes du lot), offres adverses seulement si le mode les montre.
 *
 * @param botSeed     aléa propre au bot, dérivé de la seed de la partie (jamais la seed elle-même)
 * @param hints       indices révélés du lot, tous paliers et toutes cartes confondus
 * @param lotSize     nombre de cartes du lot
 * @param freeSlots   slots encore libres dans l'équipe du bot
 * @param totalSlots  taille d'une équipe
 * @param visibleBids offres visibles (mode open), vide sinon
 * @param drawnCard   carte piochée par ce bot (mode à piocheur), null sinon : seul le piocheur la voit
 * @param unseen      cartes du thème que personne n'a encore gagnées ni vues défaussées :
 *                    connaissance publique d'un joueur qui connaît l'univers, sans l'ordre du deck
 */
public record BotView(String botId, long botSeed, String bidMode, boolean bidsVisible,
                      int round, int totalRounds, int budget, int startBudget, int freeSlots, int totalSlots,
                      int hintLevel, int maxHintLevel, List<Hint> hints, int lotSize, int floorPrice,
                      Integer myBid, List<Bid> visibleBids, List<Card> unseen, Card drawnCard) {

    public BotView {
        hints = List.copyOf(hints);
        visibleBids = List.copyOf(visibleBids);
        unseen = List.copyOf(unseen);
    }

    public boolean lastHint() {
        return hintLevel >= maxHintLevel;
    }

    public int roundsLeft() {
        return totalRounds - round + 1;
    }

    /** Meilleure offre visible d'un autre joueur. */
    public Optional<Bid> leader() {
        return visibleBids.stream().max((a, b) -> Integer.compare(a.amount(), b.amount()));
    }
}
