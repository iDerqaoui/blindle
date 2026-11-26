package me.blindle.realtime.room;

import me.blindle.core.GameEngine;
import me.blindle.core.GameException;
import me.blindle.core.Phase;
import me.blindle.core.Step;
import me.blindle.core.model.Bid;
import me.blindle.core.model.Card;
import me.blindle.core.model.GameSettings;
import me.blindle.core.model.GameState;
import me.blindle.core.model.PlayerState;
import me.blindle.core.model.SlotDef;
import me.blindle.modes.Vote;
import me.blindle.realtime.RealtimeConfig.RoomSettings;
import me.blindle.realtime.game.GameSetup;
import me.blindle.realtime.timer.TimerScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Une partie en cours : tient le {@link GameState} courant, enchaîne les phases
 * automatiquement et pilote les minuteries serveur. Toutes les méthodes publiques sont
 * synchronisées : une seule action à la fois par room.
 */
public final class Room {

    private static final Logger LOG = LoggerFactory.getLogger(Room.class);
    private static final int MAX_NAME = 20;

    private final String code;
    private final GameSetup setup;
    private final GameEngine engine;
    private final RoomSettings settings;
    private final TimerScheduler timers;
    private final Broadcaster broadcaster;
    private final Clock clock;

    private GameState state;
    private String hostId;
    private final List<Vote> votes = new ArrayList<>();
    private Instant phaseDeadline;
    private TimerScheduler.Handle timer;
    private long timerToken;
    private volatile Instant lastActivity;

    public Room(String code, long seed, GameSetup setup, RoomSettings settings, TimerScheduler timers,
                Broadcaster broadcaster, Clock clock) {
        this.code = code;
        this.setup = setup;
        this.engine = setup.engine();
        this.settings = settings;
        this.timers = timers;
        this.broadcaster = broadcaster;
        this.clock = clock;
        this.state = engine.create(code, seed, setup.preset().settings().toGameSettings(),
                setup.theme().toSlots(), setup.theme().toCards());
        this.lastActivity = clock.instant();
    }

    public String code() {
        return code;
    }

    public Instant lastActivity() {
        return lastActivity;
    }

    public synchronized RoomSnapshot snapshot() {
        return new RoomSnapshot(code, setup, state, hostId, phaseDeadline, votes, clock.instant());
    }

    public synchronized boolean hasPlayer(String playerId) {
        return state.player(playerId).isPresent();
    }

    // ------------------------------------------------------------------ actions joueurs

    public synchronized void join(String playerId, String name) {
        if (state.player(playerId).isPresent()) {
            broadcaster.sendTo(snapshot(), playerId);
            return;
        }
        apply(engine.addPlayer(state, playerId, cleanName(name), false));
        if (hostId == null) {
            hostId = playerId;
        }
        publish();
    }

    public synchronized void sync(String playerId) {
        requirePlayer(playerId);
        broadcaster.sendTo(snapshot(), playerId);
    }

    public synchronized void updateSettings(String playerId, Integer budget, Integer rounds, Integer bidTimerSec) {
        requireHost(playerId);
        GameSettings s = state.settings();
        int newRounds = rounds == null ? s.rounds() : rounds;
        if (newRounds > state.deck().size()) {
            throw new GameException("TOO_MANY_ROUNDS", "Pas assez de cartes pour " + newRounds + " rounds");
        }
        int timer = bidTimerSec == null ? s.bidTimerSec() : bidTimerSec;
        if (timer < 5 || timer > 600) {
            throw new GameException("BAD_TIMER", "Le timer doit être entre 5 et 600 secondes");
        }
        GameSettings updated;
        try {
            updated = new GameSettings(budget == null ? s.budget() : budget, newRounds, s.teamSize(),
                    s.hintLevels(), timer);
        } catch (IllegalArgumentException e) {
            throw new GameException("BAD_SETTINGS", "Paramètres invalides");
        }
        apply(engine.updateSettings(state, updated));
        publish();
    }

    public synchronized void start(String playerId) {
        requireHost(playerId);
        if (state.players().size() < settings.minPlayers()) {
            throw new GameException("NOT_ENOUGH_PLAYERS",
                    "Il faut au moins " + settings.minPlayers() + " joueurs");
        }
        apply(engine.start(state));
        beginRound();
        publish();
    }

    public synchronized void bid(String playerId, int amount) {
        requirePlayer(playerId);
        apply(engine.placeBid(state, new Bid(playerId, amount)));
        closeTierIfComplete();
        publish();
    }

    public synchronized void pass(String playerId) {
        requirePlayer(playerId);
        apply(engine.pass(state, playerId));
        closeTierIfComplete();
        publish();
    }

    public synchronized void assignSlot(String playerId, String slotId) {
        requirePlayer(playerId);
        if (state.phase() != Phase.REVEALED) {
            throw new GameException("WRONG_PHASE", "Aucune carte à placer");
        }
        Card card = nextCardToPlace(playerId);
        apply(engine.assignSlot(state, playerId, slotId, card.id()));
        afterSlot();
        publish();
    }

    public synchronized void vote(String playerId, String teamOwnerId) {
        requirePlayer(playerId);
        Vote vote = new Vote(playerId, teamOwnerId);
        setup.verdictMode().validateVote(state, vote, votes);
        votes.add(vote);
        if (setup.verdictMode().votesComplete(state, votes)) {
            finishVerdict();
        }
        publish();
    }

    // ------------------------------------------------------------------ enchaînement

    /** Tire le lot suivant (ou passe au verdict) et ouvre le premier palier. */
    private void beginRound() {
        apply(engine.nextRound(state));
        continueRound();
    }

    /** Après un tirage (DRAW) : premier indice puis enchères ; ou verdict si la partie est finie. */
    private void continueRound() {
        if (state.phase() == Phase.VERDICT) {
            enterVerdict();
            return;
        }
        apply(engine.revealFirstHint(state));
        openTier();
    }

    private void openTier() {
        Instant deadline = clock.instant().plusSeconds(state.settings().bidTimerSec());
        apply(engine.openBidding(state, deadline));
        phaseDeadline = null;
        schedule(deadline);
        // Personne ne peut miser (équipes pleines) : inutile d'attendre.
        closeTierIfComplete();
    }

    private void closeTierIfComplete() {
        if (state.phase() == Phase.BIDDING && setup.bidMode().isComplete(state)) {
            closeTier();
        }
    }

    private void closeTier() {
        cancelTimer();
        apply(engine.closeBidding(state, setup.bidMode()));
        switch (state.phase()) {
            case HINT -> openTier();
            case AWARDED -> {
                apply(engine.revealCard(state));
                phaseDeadline = clock.instant().plusSeconds(settings.slotTimeoutSec());
                schedule(phaseDeadline);
            }
            case DRAW, VERDICT -> continueRound();
            default -> throw new IllegalStateException("Phase inattendue après enchères : " + state.phase());
        }
    }

    private void afterSlot() {
        if (state.phase() == Phase.SLOT) {
            cancelTimer();
            phaseDeadline = null;
            beginRound();
        }
    }

    /** Le gagnant n'a pas choisi à temps : premier slot libre compatible, sinon premier libre. */
    private void autoPlace() {
        String winner = state.lot().winnerId();
        while (state.phase() == Phase.REVEALED) {
            Card card = nextCardToPlace(winner);
            PlayerState p = state.player(winner).orElseThrow();
            List<SlotDef> free = state.slots().stream().filter(s -> !p.team().containsKey(s.id())).toList();
            SlotDef slot = free.stream().filter(s -> card.tags().containsAll(s.requiredTags())).findFirst()
                    .orElse(free.get(0));
            apply(engine.assignSlot(state, winner, slot.id(), card.id()));
        }
        afterSlot();
    }

    private void enterVerdict() {
        cancelTimer();
        if (setup.verdictMode().usesVotes()) {
            phaseDeadline = clock.instant().plusSeconds(settings.voteTimeoutSec());
            schedule(phaseDeadline);
        } else {
            finishVerdict();
        }
    }

    private void finishVerdict() {
        cancelTimer();
        phaseDeadline = null;
        apply(engine.finish(state, setup.verdictMode().compute(state, votes)));
    }

    // ------------------------------------------------------------------ minuteries

    private void schedule(Instant at) {
        cancelTimer();
        long token = ++timerToken;
        timer = timers.schedule(at, () -> onTimeout(token));
    }

    private void cancelTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        timerToken++;
    }

    synchronized void onTimeout(long token) {
        if (token != timerToken) {
            return; // minuterie périmée
        }
        timer = null;
        try {
            switch (state.phase()) {
                case BIDDING -> closeTier();
                case REVEALED -> autoPlace();
                case VERDICT -> finishVerdict();
                default -> {
                    return;
                }
            }
            publish();
        } catch (RuntimeException e) {
            LOG.error("Room {} : erreur à l'expiration du timer en phase {}", code, state.phase(), e);
        }
    }

    // ------------------------------------------------------------------ utilitaires

    private void apply(Step step) {
        state = step.state();
        lastActivity = clock.instant();
    }

    private void publish() {
        broadcaster.publish(snapshot());
    }

    private Card nextCardToPlace(String playerId) {
        PlayerState p = state.player(playerId).orElseThrow();
        return state.lot().cards().stream().filter(c -> !p.team().containsValue(c.id())).findFirst()
                .orElseThrow(() -> new GameException("NOTHING_TO_PLACE", "Aucune carte à placer"));
    }

    private void requirePlayer(String playerId) {
        if (state.player(playerId).isEmpty()) {
            throw new GameException("NOT_IN_ROOM", "Tu ne fais pas partie de cette room");
        }
    }

    private void requireHost(String playerId) {
        requirePlayer(playerId);
        if (!playerId.equals(hostId)) {
            throw new GameException("NOT_HOST", "Seul l'hôte peut faire ça");
        }
    }

    private static String cleanName(String name) {
        String n = name == null ? "" : name.strip().replaceAll("\\s+", " ");
        if (n.isEmpty() || n.length() > MAX_NAME) {
            throw new GameException("BAD_NAME", "Pseudo de 1 à " + MAX_NAME + " caractères");
        }
        return n;
    }
}
