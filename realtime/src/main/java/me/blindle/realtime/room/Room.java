package me.blindle.realtime.room;

import me.blindle.core.GameEngine;
import me.blindle.core.GameException;
import me.blindle.core.Phase;
import me.blindle.core.Step;
import me.blindle.core.event.GameEvent;
import me.blindle.core.model.Bid;
import me.blindle.core.model.Card;
import me.blindle.core.model.GameSettings;
import me.blindle.core.model.GameState;
import me.blindle.core.model.LotState;
import me.blindle.core.model.PlayerState;
import me.blindle.core.model.SlotDef;
import me.blindle.modes.BidMode;
import me.blindle.modes.Vote;
import me.blindle.modes.bot.BotStrategy;
import me.blindle.modes.bot.BotViews;
import me.blindle.realtime.RealtimeConfig.RoomSettings;
import me.blindle.realtime.game.GameSetup;
import me.blindle.realtime.timer.TimerScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Une partie en cours : tient le {@link GameState} courant, enchaîne les phases
 * automatiquement et pilote les minuteries serveur. Toutes les méthodes publiques sont
 * synchronisées : une seule action à la fois par room.
 */
public final class Room {

    private static final Logger LOG = LoggerFactory.getLogger(Room.class);
    private static final int MAX_NAME = 20;
    private static final int MAX_BOT_TURNS = 200;
    static final int BOT_REVEAL_SEC = 4;

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
    private final List<RoundOutcome> history = new ArrayList<>();
    private final DailyContext daily;
    private Integer dailyRank;
    private final Map<String, BotStrategy> bots = new LinkedHashMap<>();

    public Room(String code, long seed, GameSetup setup, RoomSettings settings, TimerScheduler timers,
                Broadcaster broadcaster, Clock clock) {
        this(code, seed, setup, settings, timers, broadcaster, clock, null);
    }

    /**
     * @param daily défi du jour (classement en fin de partie), ou null pour une partie normale
     */
    public Room(String code, long seed, GameSetup setup, RoomSettings settings, TimerScheduler timers,
                Broadcaster broadcaster, Clock clock, DailyContext daily) {
        this.daily = daily;
        this.code = code;
        this.setup = setup;
        this.engine = setup.engine();
        this.settings = settings;
        this.timers = timers;
        this.broadcaster = broadcaster;
        this.clock = clock;
        this.state = engine.create(code, seed, setup.preset().settings().toGameSettings(),
                setup.theme().toSlots(), setup.deck());
        this.lastActivity = clock.instant();
    }

    public String code() {
        return code;
    }

    public Instant lastActivity() {
        return lastActivity;
    }

    public synchronized RoomSnapshot snapshot() {
        return new RoomSnapshot(code, setup, state, hostId, phaseDeadline, votes, clock.instant(), history,
                daily == null ? null : daily.date(), dailyRank);
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
        long humans = state.players().stream().filter(p -> !p.bot()).count();
        if (humans >= settings.maxPlayers()) {
            throw new GameException("ROOM_FULL", "La partie est complète");
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
        if (state.phase() != Phase.BIDDING) {
            throw new GameException("WRONG_PHASE", "Les enchères sont fermées");
        }
        Bid bid = new Bid(playerId, amount);
        setup.bidMode().validateBid(state, bid);
        Instant before = state.lot().deadline();
        apply(engine.placeBid(state, bid, clock.instant()));
        if (setup.bidMode().bidsVisible()) {
            runBots(); // les bots voient la nouvelle offre et peuvent surenchérir
        }
        rescheduleIfExtended(before);
        closeTierIfComplete();
        publish();
    }

    /** Le joueur hôte ajoute un bot du profil donné (en LOBBY). Interdit en défi du jour. */
    public synchronized void addBot(String playerId, BotStrategy strategy) {
        requireHost(playerId);
        if (daily != null) {
            throw new GameException("DAILY_IS_SOLO", "Le défi du jour se joue seul, sans bot");
        }
        String id = "bot-" + (bots.size() + 1);
        long same = bots.values().stream().filter(b -> b.profile().equals(strategy.profile())).count();
        String name = "Bot " + strategy.label() + (same > 0 ? " " + (same + 1) : "");
        apply(engine.addPlayer(state, id, name, true));
        bots.put(id, strategy);
        publish();
    }

    public synchronized void pass(String playerId) {
        requirePlayer(playerId);
        if (state.phase() != Phase.BIDDING) {
            throw new GameException("WRONG_PHASE", "Les enchères sont fermées");
        }
        setup.bidMode().validatePass(state, playerId);
        apply(engine.pass(state, playerId));
        closeTierIfComplete();
        publish();
    }

    public synchronized void useJoker(String playerId, String jokerId, String targetId) {
        requirePlayer(playerId);
        apply(engine.useJoker(state, playerId, jokerId, targetId));
        // Un vol change le nombre de slots libres, donc les joueurs éligibles.
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

    /** Le gagnant renonce à la carte, uniquement si aucun slot libre ne l'accepte. */
    public synchronized void forfeit(String playerId) {
        requirePlayer(playerId);
        if (state.phase() != Phase.REVEALED) {
            throw new GameException("WRONG_PHASE", "Aucune carte à placer");
        }
        if (!placeableSlots(state, engine, playerId).isEmpty()) {
            throw new GameException("CAN_PLACE", "Un slot peut accueillir cette carte");
        }
        apply(engine.forfeit(state, playerId));
        afterSlot();
        publish();
    }

    /** Slots libres où la prochaine carte du lot peut aller, règles comprises (essai à blanc). */
    public static List<String> placeableSlots(GameState s, GameEngine engine, String playerId) {
        if (s.phase() != Phase.REVEALED || s.lot() == null || !playerId.equals(s.lot().winnerId())) {
            return List.of();
        }
        PlayerState p = s.player(playerId).orElseThrow();
        Card card = s.lot().cards().stream().filter(c -> !p.team().containsValue(c.id())).findFirst().orElse(null);
        if (card == null) {
            return List.of();
        }
        return s.slots().stream().map(SlotDef::id)
                .filter(id -> !p.team().containsKey(id))
                .filter(id -> engine.canAssign(s, playerId, id, card.id()))
                .toList();
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
        runBots();
        rescheduleIfExtended(deadline);
        // Tout le monde a joué, ou personne ne peut miser (équipes pleines) : inutile d'attendre.
        closeTierIfComplete();
    }

    /**
     * Fait jouer les bots éligibles sur le palier courant, par le même chemin qu'un humain
     * (validation du mode, règles). Offre secrète : une décision par palier. Offre visible :
     * on recommence tant qu'un bot surenchérit ; le meneur ne rejoue pas, un bot qui a passé non plus.
     */
    private void runBots() {
        BidMode mode = setup.bidMode();
        for (int turn = 0; turn < MAX_BOT_TURNS; turn++) {
            boolean acted = false;
            for (Map.Entry<String, BotStrategy> bot : bots.entrySet()) {
                if (state.phase() != Phase.BIDDING) {
                    return;
                }
                String id = bot.getKey();
                LotState lot = state.lot();
                if (!state.eligibleBidders().contains(id) || lot.passes().contains(id)
                        || (!mode.bidsVisible() && lot.hasActed(id)) || id.equals(leaderId(lot))) {
                    continue;
                }
                acted |= botActs(id, bot.getValue().decide(BotViews.of(state, id, mode)));
            }
            if (!acted || !mode.bidsVisible()) {
                return;
            }
        }
    }

    /** Applique la décision d'un bot ; une offre refusée par une règle devient une passe. */
    private boolean botActs(String botId, Optional<Bid> decision) {
        if (decision.isPresent()) {
            try {
                setup.bidMode().validateBid(state, decision.get());
                apply(engine.placeBid(state, decision.get(), clock.instant()));
                return true;
            } catch (GameException e) {
                // offre refusée (plancher, budget…) : le bot passe
            }
        }
        try {
            setup.bidMode().validatePass(state, botId);
            apply(engine.pass(state, botId));
            return true;
        } catch (GameException e) {
            return false;
        }
    }

    private static String leaderId(LotState lot) {
        return lot.bids().stream().max(Comparator.comparingInt(Bid::amount)).map(Bid::playerId).orElse(null);
    }

    /** Heure de fin repoussée par une règle (anti-snipe) : on reprogramme la minuterie. */
    private void rescheduleIfExtended(Instant before) {
        if (state.phase() != Phase.BIDDING) {
            return;
        }
        Instant after = state.lot().deadline();
        if (after != null && !after.equals(before)) {
            schedule(after);
        }
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
                // Un bot place sa carte seul, après une courte pause pour laisser voir la révélation.
                int wait = bots.containsKey(state.lot().winnerId()) ? BOT_REVEAL_SEC : settings.slotTimeoutSec();
                phaseDeadline = clock.instant().plusSeconds(wait);
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

    /**
     * Le gagnant n'a pas choisi à temps : premier slot accepté par les règles,
     * en privilégiant ceux dont la carte a les tags ; sinon la carte est défaussée.
     */
    private void autoPlace() {
        String winner = state.lot().winnerId();
        while (state.phase() == Phase.REVEALED) {
            Card card = nextCardToPlace(winner);
            List<String> options = placeableSlots(state, engine, winner);
            if (options.isEmpty()) {
                apply(engine.forfeit(state, winner));
                break;
            }
            String slot = options.stream()
                    .filter(id -> state.slot(id).map(s -> card.tags().containsAll(s.requiredTags())
                            && !s.requiredTags().isEmpty()).orElse(false))
                    .findFirst().orElse(options.get(0));
            apply(engine.assignSlot(state, winner, slot, card.id()));
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
        if (daily != null) {
            try {
                dailyRank = daily.submit().applyAsInt(snapshot());
            } catch (RuntimeException e) {
                LOG.error("Room {} : score du défi du jour non enregistré", code, e);
            }
        }
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
        recordOutcomes(state, step);
        state = step.state();
        lastActivity = clock.instant();
    }

    /** Relève l'issue des rounds : lot gagné (au palier courant) ou invendu. */
    private void recordOutcomes(GameState before, Step step) {
        for (GameEvent event : step.events()) {
            LotState lot = before.lot();
            if (lot == null) {
                continue;
            }
            int max = Math.min(lot.maxHintLevel(), before.settings().hintLevels());
            List<String> ids = lot.cards().stream().map(Card::id).toList();
            if (event instanceof GameEvent.LotWon won) {
                history.add(new RoundOutcome(before.roundIndex(), ids, won.playerId(), lot.hintLevel(), max,
                        won.price()));
            } else if (event instanceof GameEvent.RoundEnded ended && !ended.sold()) {
                history.add(new RoundOutcome(before.roundIndex(), ids, null, lot.hintLevel(), max, 0));
            }
        }
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
