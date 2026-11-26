package me.blindle.core;

import me.blindle.core.event.GameEvent;
import me.blindle.core.model.Bid;
import me.blindle.core.model.Card;
import me.blindle.core.model.GameResult;
import me.blindle.core.model.GameSettings;
import me.blindle.core.model.GameState;
import me.blindle.core.model.LotResult;
import me.blindle.core.model.LotState;
import me.blindle.core.model.PlayerState;
import me.blindle.core.model.SlotDef;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Orchestration d'une partie : chaque action vérifie la phase, produit un nouvel état,
 * émet ses événements vers le {@link HookDispatcher} (règles) et renvoie un {@link Step}.
 * Une action rejetée lève une {@link GameException} sans altérer l'état d'entrée.
 * <p>
 * Le moteur ne connaît ni horloge ni aléa propre : l'heure de fin est fournie par l'appelant,
 * l'aléa provient de la seed via {@link GameRandom}.
 */
public final class GameEngine {

    public static final int MAX_PLAYERS = 8;

    private final PhaseMachine phases = new PhaseMachine();
    private final HookDispatcher dispatcher;

    public GameEngine(HookDispatcher dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    public GameEngine() {
        this(HookDispatcher.NONE);
    }

    // ---------------------------------------------------------------- lobby

    public GameState create(String gameId, long seed, GameSettings settings,
                            List<SlotDef> slots, List<Card> cards) {
        if (slots.isEmpty()) {
            throw new IllegalArgumentException("Au moins un slot requis");
        }
        return new GameState(gameId, seed, Phase.LOBBY, settings, slots, List.of(), cards,
                0, null, List.of(), List.of(), null, 0);
    }

    public Step addPlayer(GameState s, String playerId, String name, boolean bot) {
        phases.requirePhase(s.phase(), Phase.LOBBY);
        if (s.player(playerId).isPresent()) {
            throw new GameException("DUPLICATE_PLAYER", "Joueur déjà présent : " + name);
        }
        if (s.players().size() >= MAX_PLAYERS) {
            throw new GameException("ROOM_FULL", "La partie est complète");
        }
        List<PlayerState> ps = new ArrayList<>(s.players());
        ps.add(PlayerState.join(playerId, name, bot));
        return new Tx(s.withPlayers(ps).withLog(name + " rejoint la partie")).done();
    }

    public Step updateSettings(GameState s, GameSettings settings) {
        phases.requirePhase(s.phase(), Phase.LOBBY);
        return new Tx(s.withSettings(settings)).done();
    }

    // ---------------------------------------------------------------- partie

    /** LOBBY → STARTED : budgets distribués, deck mélangé par la seed. */
    public Step start(GameState s) {
        if (s.players().isEmpty()) {
            throw new GameException("NO_PLAYERS", "Aucun joueur dans la partie");
        }
        Tx tx = new Tx(s);
        tx.to(Phase.STARTED);
        int budget = s.settings().budget();
        List<PlayerState> ps = new ArrayList<>();
        for (PlayerState p : s.players()) {
            ps.add(p.withBudget(budget));
        }
        List<Card> deck = GameRandom.derive(s.seed(), "deck").shuffled(s.deck());
        tx.state = tx.state.withPlayers(ps).withDeck(deck).withLog("La partie commence");
        tx.emit(new GameEvent.GameStarted());
        return tx.done();
    }

    /**
     * Passe au round suivant (STARTED ou SLOT) : tire un lot, ou passe au verdict
     * si tous les rounds sont joués ou le deck épuisé.
     */
    public Step nextRound(GameState s) {
        phases.requirePhase(s.phase(), Phase.STARTED, Phase.SLOT);
        Tx tx = new Tx(s);
        if (s.phase() == Phase.SLOT) {
            tx.emit(new GameEvent.RoundEnded(s.roundIndex(), true));
            tx.state = tx.state.withLot(null);
        }
        drawOrVerdict(tx);
        return tx.done();
    }

    /** DRAW → HINT : révèle le premier indice. */
    public Step revealFirstHint(GameState s) {
        Tx tx = new Tx(s);
        tx.to(Phase.HINT);
        revealHint(tx, 1);
        return tx.done();
    }

    /** HINT → BIDDING : ouvre les enchères jusqu'à {@code deadline} (heure serveur). */
    public Step openBidding(GameState s, Instant deadline) {
        Tx tx = new Tx(s);
        tx.to(Phase.BIDDING);
        tx.state = tx.state.withLot(tx.state.lot().withDeadline(deadline));
        return tx.done();
    }

    public Step placeBid(GameState s, Bid bid) {
        phases.requirePhase(s.phase(), Phase.BIDDING);
        PlayerState p = s.player(bid.playerId())
                .orElseThrow(() -> new GameException("UNKNOWN_PLAYER", "Joueur inconnu"));
        LotState lot = s.lot();
        if (bid.amount() <= 0) {
            throw new GameException("BID_NOT_POSITIVE", "L'offre doit être positive");
        }
        if (bid.amount() < lot.floorPrice()) {
            throw new GameException("BID_BELOW_FLOOR",
                    "Offre minimale : " + lot.floorPrice());
        }
        if (s.freeSlots(p.id()) < lot.cards().size()) {
            throw new GameException("TEAM_FULL", "Ton équipe est complète");
        }
        Tx tx = new Tx(s.withLot(lot.withBid(bid)));
        tx.emit(new GameEvent.BidPlaced(bid));
        return tx.done();
    }

    /** Le joueur passe sur le palier courant ; son éventuelle offre est retirée. */
    public Step pass(GameState s, String playerId) {
        phases.requirePhase(s.phase(), Phase.BIDDING);
        if (s.player(playerId).isEmpty()) {
            throw new GameException("UNKNOWN_PLAYER", "Joueur inconnu");
        }
        return new Tx(s.withLot(s.lot().withPass(playerId))).done();
    }

    /**
     * Clôt le palier courant. Gagnant → AWARDED (budget débité). Sinon indice suivant,
     * ou lot invendu après le dernier palier → round suivant / verdict.
     */
    public Step closeBidding(GameState s, BidResolver resolver) {
        phases.requirePhase(s.phase(), Phase.BIDDING);
        LotState lot = s.lot();
        GameRandom random = GameRandom.derive(s.seed(),
                "bid:" + s.roundIndex() + ":" + lot.hintLevel());
        Optional<LotResult> result = resolver.resolve(lot, random);
        Tx tx = new Tx(s);
        if (result.isPresent()) {
            award(tx, result.get());
        } else if (lot.hintLevel() < maxHintLevel(s)) {
            tx.to(Phase.HINT);
            revealHint(tx, lot.hintLevel() + 1);
        } else {
            tx.state = tx.state.withLog("Lot invendu");
            List<Card> discarded = new ArrayList<>(s.discarded());
            discarded.addAll(lot.cards());
            tx.state = tx.state.withDiscarded(discarded);
            tx.emit(new GameEvent.RoundEnded(s.roundIndex(), false));
            tx.state = tx.state.withLot(null);
            drawOrVerdict(tx);
        }
        return tx.done();
    }

    /** AWARDED → REVEALED : la carte est dévoilée à tous. */
    public Step revealCard(GameState s) {
        Tx tx = new Tx(s);
        tx.to(Phase.REVEALED);
        LotState lot = s.lot();
        List<String> ids = lot.cards().stream().map(Card::id).toList();
        String names = String.join(", ", lot.cards().stream().map(Card::name).toList());
        tx.state = tx.state.withLog("Carte révélée : " + names);
        tx.emit(new GameEvent.CardRevealed(lot.winnerId(), ids));
        return tx.done();
    }

    /**
     * Le gagnant place une carte du lot dans un slot libre (définitif).
     * Passe en SLOT quand toutes les cartes du lot sont placées.
     */
    public Step assignSlot(GameState s, String playerId, String slotId, String cardId) {
        phases.requirePhase(s.phase(), Phase.REVEALED);
        LotState lot = s.lot();
        if (!playerId.equals(lot.winnerId())) {
            throw new GameException("NOT_WINNER", "Seul le gagnant place la carte");
        }
        if (lot.cards().stream().noneMatch(c -> c.id().equals(cardId))) {
            throw new GameException("UNKNOWN_CARD", "Carte hors du lot");
        }
        if (s.slot(slotId).isEmpty()) {
            throw new GameException("UNKNOWN_SLOT", "Slot inconnu : " + slotId);
        }
        PlayerState p = s.player(playerId).orElseThrow();
        if (p.team().containsKey(slotId)) {
            throw new GameException("SLOT_TAKEN", "Slot déjà occupé");
        }
        if (p.team().containsValue(cardId)) {
            throw new GameException("CARD_ALREADY_PLACED", "Carte déjà placée");
        }
        Tx tx = new Tx(s.withPlayer(playerId, pl -> pl.withCardInSlot(slotId, cardId)));
        tx.emit(new GameEvent.SlotAssigned(playerId, slotId, cardId));
        PlayerState after = tx.state.player(playerId).orElseThrow();
        boolean allPlaced = lot.cards().stream().allMatch(c -> after.team().containsValue(c.id()));
        if (allPlaced) {
            tx.to(Phase.SLOT);
        }
        return tx.done();
    }

    /** VERDICT → FINISHED avec le résultat calculé par le mode de verdict. */
    public Step finish(GameState s, GameResult result) {
        Tx tx = new Tx(s);
        tx.to(Phase.FINISHED);
        tx.state = tx.state.withResult(result).withLog("Partie terminée");
        return tx.done();
    }

    // ---------------------------------------------------------------- interne

    private int maxHintLevel(GameState s) {
        return Math.min(s.lot().maxHintLevel(), s.settings().hintLevels());
    }

    private void drawOrVerdict(Tx tx) {
        GameState s = tx.state;
        if (s.roundIndex() >= s.settings().rounds() || s.deck().isEmpty()) {
            tx.to(Phase.VERDICT);
            tx.state = tx.state.withLog("Place au verdict");
            tx.emit(new GameEvent.VerdictRequested());
            return;
        }
        tx.to(Phase.DRAW);
        Card card = s.deck().get(0);
        int round = s.roundIndex() + 1;
        tx.state = tx.state
                .withDeck(s.deck().subList(1, s.deck().size()))
                .withRoundIndex(round)
                .withLot(LotState.of(List.of(card)))
                .withLog("Round " + round + " : un lot mystère est tiré");
        tx.emit(new GameEvent.RoundStarted(round, List.of(card.id())));
    }

    private void revealHint(Tx tx, int level) {
        tx.state = tx.state.withLot(tx.state.lot().withHintLevel(level))
                .withLog("Indice " + level + " révélé");
        tx.emit(new GameEvent.HintRevealed(tx.state.roundIndex(), level));
    }

    private void award(Tx tx, LotResult result) {
        PlayerState winner = tx.state.player(result.winnerId())
                .orElseThrow(() -> new GameException("UNKNOWN_PLAYER", "Gagnant inconnu"));
        if (result.price() > winner.budget()) {
            throw new GameException("OVER_BUDGET", "Prix supérieur au budget du gagnant");
        }
        tx.to(Phase.AWARDED);
        tx.state = tx.state
                .withPlayer(winner.id(), p -> p.withBudget(p.budget() - result.price()))
                .withLot(tx.state.lot().withWinner(winner.id(), result.price()))
                .withLog(winner.name() + " remporte le lot pour " + result.price() + " pièces");
        tx.emit(new GameEvent.LotWon(winner.id(), result.price()));
    }

    /** Transaction locale à une action : accumule état et événements. */
    private final class Tx {
        private GameState state;
        private final List<GameEvent> events = new ArrayList<>();

        private Tx(GameState state) {
            this.state = state;
        }

        private void to(Phase target) {
            state = state.withPhase(phases.transition(state.phase(), target));
        }

        private void emit(GameEvent event) {
            events.add(event);
            state = dispatcher.dispatch(event, state);
        }

        private Step done() {
            return new Step(state.nextVersion(), events);
        }
    }
}
