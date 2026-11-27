package me.blindle.realtime.view;

import me.blindle.content.Theme;
import me.blindle.core.Phase;
import me.blindle.core.model.Bid;
import me.blindle.core.model.Card;
import me.blindle.core.model.GameState;
import me.blindle.core.model.LotState;
import me.blindle.core.model.PlayerState;
import me.blindle.core.model.SlotDef;
import me.blindle.modes.CardValuation;
import me.blindle.modes.RarityPoints;
import me.blindle.modes.Vote;
import me.blindle.core.rule.Rule;
import me.blindle.realtime.game.GameSetup;
import me.blindle.realtime.room.Room;
import me.blindle.realtime.room.RoomSnapshot;
import me.blindle.realtime.solo.DailyShare;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Seul endroit qui décide de ce que chaque joueur voit (principe 2) :
 * <ul>
 *   <li>l'identité des cartes du lot reste cachée jusqu'à la révélation ;</li>
 *   <li>seuls les indices déjà révélés sont envoyés ;</li>
 *   <li>en mode à offres secrètes, aucun montant avant la résolution du palier ;</li>
 *   <li>le deck et la seed ne sont jamais envoyés.</li>
 * </ul>
 */
public final class ViewProjector {

    private static final int LOG_SIZE = 30;
    private static final Set<Phase> CARD_VISIBLE = EnumSet.of(Phase.REVEALED, Phase.SLOT);
    private static final Set<Phase> RESOLVED = EnumSet.of(Phase.AWARDED, Phase.REVEALED, Phase.SLOT);

    private ViewProjector() {
    }

    public static Views.RoomView room(RoomSnapshot r) {
        GameState s = r.state();
        Theme theme = r.setup().theme();
        // Les cartes placées dans les équipes ont été révélées à tous : elles sont publiques.
        Map<String, Card> known = s.cards();
        List<Views.PlayerSummary> players = new ArrayList<>();
        for (PlayerState p : s.players()) {
            List<Views.TeamEntry> team = new ArrayList<>();
            for (SlotDef slot : s.slots()) {
                String cardId = p.team().get(slot.id());
                if (cardId != null) {
                    team.add(new Views.TeamEntry(slot.id(), card(known.get(cardId), theme), p.paid().get(cardId)));
                }
            }
            boolean acted = s.phase() == Phase.BIDDING && s.lot() != null && s.lot().hasActed(p.id());
            players.add(new Views.PlayerSummary(p.id(), p.name(), p.budget(), p.bonus(), p.id().equals(r.hostId()),
                    p.bot(), r.botProfiles().get(p.id()), acted, team, !r.disconnected().contains(p.id())));
        }
        List<Views.SlotView> slots = s.slots().stream()
                .map(x -> new Views.SlotView(x.id(), x.label(), x.requiredTags())).toList();
        Instant deadline = s.phase() == Phase.BIDDING && s.lot() != null ? s.lot().deadline() : r.phaseDeadline();
        List<String> log = s.log().subList(Math.max(0, s.log().size() - LOG_SIZE), s.log().size());
        Views.ResultView result = s.result() == null ? null
                : new Views.ResultView(s.result().ranking(), s.result().scores());
        return new Views.RoomView("room", r.code(), s.version(), s.phase().name(),
                r.setup().preset().id(), r.setup().preset().name(), r.setup().theme().name(),
                r.setup().bidMode().id(), r.setup().verdictMode().id(),
                r.setup().activeRules().stream().map(x -> x.ref()).toList(), r.hostId(),
                s.roundIndex(), s.settings().rounds(), slots, players, lot(s, r.setup().bidMode().bidsVisible(), r.setup().bidMode().drawerId(s) != null, theme),
                deadline == null ? null : deadline.toEpochMilli(), r.now().toEpochMilli(),
                List.copyOf(log), result, r.votes().size(), daily(r), drawer(r),
                s.discarded().stream().map(c -> card(c, theme)).toList(), art(theme),
                !r.setup().overrides().isEmpty(), r.setup().deck().size(), ruleParams(r));
    }

    /** Paramètres des règles actives (défauts compris) : le client affiche les vrais montants. */
    private static Map<String, Map<String, Object>> ruleParams(RoomSnapshot r) {
        Map<String, Map<String, Object>> out = new java.util.LinkedHashMap<>();
        r.setup().activeRules().forEach(x -> out.put(x.ref(), x.params()));
        return out;
    }

    /** Catalogue de personnalisation de la room, pour son hôte seul. */
    public static Views.RoomCatalogView catalog(String code, GameSetup setup, List<Rule> catalog) {
        Theme theme = setup.theme();
        Set<String> inDeck = new java.util.HashSet<>();
        setup.deck().forEach(c -> inDeck.add(c.id()));
        List<Views.CatalogCard> cards = theme.cards().stream()
                .map(c -> new Views.CatalogCard(c.id(), c.name(), c.image(), c.rarity(),
                        setup.overrides().rarities().getOrDefault(c.id(), c.rarity()),
                        Boolean.TRUE.equals(c.isTrap()), inDeck.contains(c.id())))
                .toList();
        List<String> rarities = theme.cards().stream().map(Theme.CardDefinition::rarity)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<String, Map<String, Object>> active = new java.util.LinkedHashMap<>();
        setup.activeRules().forEach(r -> active.put(r.ref(), r.params()));
        List<Views.CatalogRule> rules = catalog.stream().map(rule -> {
            Map<String, Object> params = new java.util.LinkedHashMap<>(rule.defaultParams());
            if (active.containsKey(rule.type())) {
                params.putAll(active.get(rule.type()));
            }
            return new Views.CatalogRule(rule.type(), active.containsKey(rule.type()), params,
                    rule.defaultParams(), rule.requiredBidModes(), rule.conflictsWith());
        }).toList();
        return new Views.RoomCatalogView("catalog", code, cards, rarities, rules);
    }

    public static Views.PlayerView player(RoomSnapshot r, String playerId) {
        GameState s = r.state();
        PlayerState me = s.player(playerId).orElseThrow();
        LotState lot = s.lot();
        Integer myBid = null;
        boolean passed = false;
        if (lot != null) {
            myBid = lot.bids().stream().filter(b -> b.playerId().equals(playerId))
                    .map(Bid::amount).findFirst().orElse(null);
            passed = lot.passes().contains(playerId);
        }
        boolean canBid = s.phase() == Phase.BIDDING && s.eligibleBidders().contains(playerId);
        boolean mustPlace = s.phase() == Phase.REVEALED && lot != null && playerId.equals(lot.winnerId());
        List<String> freeSlots = s.slots().stream().map(SlotDef::id)
                .filter(id -> !me.team().containsKey(id)).toList();
        String votedFor = r.votes().stream().filter(v -> v.voterId().equals(playerId))
                .map(Vote::teamOwnerId).findFirst().orElse(null);
        List<String> placeable = Room.placeableSlots(s, r.setup().engine(), playerId);
        // Indices privés (joker espion) : visibles uniquement par leur propriétaire.
        List<Views.HintView> privateHints = me.privateHints().stream()
                .map(h -> new Views.HintView(h.level(), h.type(), h.value())).toList();
        return new Views.PlayerView("player", r.code(), s.version(), playerId, me.name(),
                playerId.equals(r.hostId()), myBid, passed, canBid, mustPlace, freeSlots, placeable,
                me.jokers(), privateHints, votedFor, drawnCard(r, playerId));
    }

    private static Views.LotView lot(GameState s, boolean bidsVisible, boolean drawerMode, Theme theme) {
        LotState lot = s.lot();
        if (lot == null) {
            return null;
        }
        List<Views.HintView> hints = new ArrayList<>();
        // Mode à piocheur : les autres ne voient que le dos de la carte, aucun indice.
        int shownLevels = drawerMode ? 0 : lot.hintLevel();
        for (int level = 1; level <= shownLevels; level++) {
            int l = level;
            // Lot multi-cartes : un indice par carte et par palier.
            for (Card c : lot.cards()) {
                c.hint(l).ifPresent(h -> hints.add(new Views.HintView(h.level(), h.type(), h.value())));
            }
        }
        boolean showBids = bidsVisible || RESOLVED.contains(s.phase());
        List<Views.BidView> bids = showBids
                ? lot.bids().stream().map(b -> new Views.BidView(b.playerId(), b.amount())).toList()
                : null;
        List<Views.CardView> cards = CARD_VISIBLE.contains(s.phase())
                ? lot.cards().stream().map(c -> card(c, theme)).toList()
                : null;
        int maxHint = Math.min(lot.maxHintLevel(), s.settings().hintLevels());
        int acted = lot.bids().size() + lot.passes().size();
        return new Views.LotView(lot.hintLevel(), maxHint, hints, lot.floorPrice(), acted, bids, cards,
                RESOLVED.contains(s.phase()) ? lot.winnerId() : null,
                RESOLVED.contains(s.phase()) ? lot.price() : null);
    }

    /** Défi du jour : partie solo, le texte à partager est celui de son unique joueur humain. */
    private static Views.DailyView daily(RoomSnapshot r) {
        if (r.dailyDate() == null) {
            return null;
        }
        String share = r.state().players().stream().filter(p -> !p.bot()).findFirst()
                .map(p -> DailyShare.text(r, p.id())).orElse(null);
        return new Views.DailyView(r.dailyDate().toString(), r.dailyRank(), share);
    }

    /** Piocheur du lot en cours (public : tout le monde sait qui a pioché), tant que la carte est cachée. */
    private static String drawer(RoomSnapshot r) {
        GameState s = r.state();
        return s.lot() == null || CARD_VISIBLE.contains(s.phase()) ? null : r.setup().bidMode().drawerId(s);
    }

    /** La carte piochée, envoyée à son seul piocheur avant la révélation (principe 2). */
    private static Views.CardView drawnCard(RoomSnapshot r, String playerId) {
        if (!playerId.equals(drawer(r))) {
            return null;
        }
        return card(r.state().lot().cards().get(0), r.setup().theme());
    }

    private static Views.ArtView art(Theme theme) {
        Theme.Art a = theme.art();
        return a == null ? null : new Views.ArtView(a.cover(), a.banner(), a.color());
    }

    private static Views.CardView card(Card c, Theme theme) {
        if (c == null) {
            return null;
        }
        return new Views.CardView(c.id(), c.name(), c.tags(), c.rarity(), c.stats(), CardValuation.of(c),
                RarityPoints.of(c), theme.imageOf(c.id()));
    }
}
