package me.blindle.modes.bot;

import me.blindle.core.GameRandom;
import me.blindle.core.model.Bid;
import me.blindle.core.model.Card;
import me.blindle.core.model.GameState;
import me.blindle.core.model.Hint;
import me.blindle.core.model.LotState;
import me.blindle.core.model.PlayerState;
import me.blindle.modes.BidMode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Construit la vue d'un bot avec les mêmes filtres que la vue d'un humain : indices révélés
 * seulement, identité des cartes du lot cachée, offres adverses seulement en enchère visible.
 */
public final class BotViews {

    private BotViews() {
    }

    public static BotView of(GameState s, String botId, BidMode mode) {
        PlayerState me = s.player(botId).orElseThrow();
        LotState lot = s.lot();
        List<Hint> hints = new ArrayList<>();
        Integer myBid = null;
        List<Bid> visible = List.of();
        int hintLevel = 0;
        int maxHint = 0;
        int lotSize = 1;
        int floor = 0;
        if (lot != null) {
            hintLevel = lot.hintLevel();
            maxHint = Math.min(lot.maxHintLevel(), s.settings().hintLevels());
            lotSize = lot.cards().size();
            floor = lot.floorPrice();
            for (Card c : lot.cards()) {
                for (int level = 1; level <= hintLevel; level++) {
                    c.hint(level).ifPresent(hints::add);
                }
            }
            myBid = lot.bids().stream().filter(b -> b.playerId().equals(botId))
                    .map(Bid::amount).findFirst().orElse(null);
            if (mode.bidsVisible()) {
                visible = lot.bids().stream().filter(b -> !b.playerId().equals(botId)).toList();
            }
        }
        // Cartes publiquement sorties du jeu : placées dans une équipe ou défaussées.
        Set<String> gone = new HashSet<>();
        s.players().forEach(p -> gone.addAll(p.team().values()));
        s.discarded().forEach(c -> gone.add(c.id()));
        List<Card> unseen = s.cards().values().stream()
                .filter(c -> !gone.contains(c.id()))
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .toList();
        long botSeed = GameRandom.derive(s.seed(), "bot:" + botId).nextLong();
        return new BotView(botId, botSeed, mode.id(), mode.bidsVisible(), s.roundIndex(), s.settings().rounds(),
                me.budget(), s.settings().budget(), s.freeSlots(botId), s.slots().size(), hintLevel, maxHint,
                hints, lotSize, floor, myBid, visible, unseen);
    }
}
