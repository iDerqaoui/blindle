package me.blindle.realtime.stomp;

import me.blindle.content.ContentRegistry;
import me.blindle.content.Preset;
import me.blindle.core.GameException;
import me.blindle.realtime.channel.ChannelAccess;
import me.blindle.realtime.stomp.PlayerHandshakeHandler.PlayerPrincipal;
import me.blindle.modes.bot.BotCatalog;
import me.blindle.realtime.room.Room;
import me.blindle.realtime.room.RoomRegistry;
import me.blindle.realtime.solo.DailyChallenge;
import me.blindle.realtime.game.GameFactory;
import me.blindle.realtime.game.RoomOverrides;
import me.blindle.realtime.view.ViewProjector;
import me.blindle.realtime.view.Views;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Commandes STOMP (préfixe {@code /app}). Le joueur est toujours {@link Principal#getName()}.
 * Protocole : docs/ARCHITECTURE.md §3.
 */
@Controller
public class GameController {

    private static final Logger LOG = LoggerFactory.getLogger(GameController.class);

    private final RoomRegistry rooms;
    private final DailyChallenge daily;
    private final BotCatalog bots;
    private final ChannelAccess access;
    private final ContentRegistry content;
    private final GameFactory factory;
    private final SessionTokens tokens;

    public GameController(RoomRegistry rooms, DailyChallenge daily, BotCatalog bots, ChannelAccess access,
                          ContentRegistry content, GameFactory factory, SessionTokens tokens) {
        this.factory = factory;
        this.tokens = tokens;
        this.access = access;
        this.content = content;
        this.rooms = rooms;
        this.daily = daily;
        this.bots = bots;
    }

    @MessageMapping("/room.create")
    @SendToUser(destinations = "/queue/session", broadcast = false)
    public Session create(Requests.CreateRoom req, Principal player) {
        access.require(preset(req.presetId()), PlayerPrincipal.channelOf(player));
        if (daily.dailyPresets().stream().anyMatch(p -> p.id().equals(req.presetId()))) {
            throw new GameException("DAILY_IS_SOLO", "Le défi du jour se joue seul");
        }
        Room room = rooms.create(req.presetId());
        room.join(player.getName(), req.name());
        return session(room, player);
    }

    /** Jeton de reconnexion, envoyé au seul joueur sur {@code /user/queue/session}. */
    public record Session(String type, String code, String token) {
    }

    private Session session(Room room, Principal player) {
        return new Session("session", room.code(), tokens.issue(player.getName()));
    }

    /** Lance le défi du jour : room solo créée, rejointe et démarrée d'un coup. */
    @MessageMapping("/daily.start")
    @SendToUser(destinations = "/queue/session", broadcast = false)
    public Session startDaily(Requests.StartDaily req, Principal player) {
        access.require(preset(req.presetId()), PlayerPrincipal.channelOf(player));
        return session(daily.start(player.getName(), req.name(), req.presetId()), player);
    }

    @MessageMapping("/room.join")
    @SendToUser(destinations = "/queue/session", broadcast = false)
    public Session join(Requests.JoinRoom req, Principal player) {
        Room room = rooms.require(req.code());
        // Un joueur ne rejoint pas une room dont le thème n'est pas ouvert sur son canal (ou en a été retiré).
        access.require(room.preset(), PlayerPrincipal.channelOf(player));
        room.join(player.getName(), req.name());
        return session(room, player);
    }

    @MessageMapping("/room/{code}/sync")
    public void sync(@DestinationVariable String code, Principal player) {
        rooms.require(code).sync(player.getName());
    }

    @MessageMapping("/room/{code}/settings")
    public void settings(@DestinationVariable String code, Requests.UpdateSettings req, Principal player) {
        rooms.require(code).updateSettings(player.getName(), req.budget(), req.rounds(), req.bidTimerSec());
    }

    @MessageMapping("/room/{code}/bot")
    public void addBot(@DestinationVariable String code, Requests.AddBot req, Principal player) {
        rooms.require(code).addBot(player.getName(), bots.require(req.profile()));
    }

    @MessageMapping("/room/{code}/start")
    public void start(@DestinationVariable String code, Principal player) {
        rooms.require(code).start(player.getName());
    }

    @MessageMapping("/room/{code}/bid")
    public void bid(@DestinationVariable String code, Requests.PlaceBid req, Principal player) {
        rooms.require(code).bid(player.getName(), req.amount());
    }

    @MessageMapping("/room/{code}/pass")
    public void pass(@DestinationVariable String code, Principal player) {
        rooms.require(code).pass(player.getName());
    }

    @MessageMapping("/room/{code}/slot")
    public void slot(@DestinationVariable String code, Requests.AssignSlot req, Principal player) {
        rooms.require(code).assignSlot(player.getName(), req.slotId());
    }

    @MessageMapping("/room/{code}/joker")
    public void joker(@DestinationVariable String code, Requests.UseJoker req, Principal player) {
        rooms.require(code).useJoker(player.getName(), req.jokerId(), req.targetId());
    }

    @MessageMapping("/room/{code}/forfeit")
    public void forfeit(@DestinationVariable String code, Principal player) {
        rooms.require(code).forfeit(player.getName());
    }

    /** L'hôte cède sa place à un autre joueur connecté. */
    @MessageMapping("/room/{code}/host")
    public void transferHost(@DestinationVariable String code, Requests.TransferHost req, Principal player) {
        rooms.require(code).transferHost(player.getName(), req.playerId());
    }

    /** Catalogue de personnalisation (cartes du thème, règles), envoyé à l'hôte seul. */
    @MessageMapping("/room/{code}/catalog")
    @SendToUser(destinations = "/queue/catalog", broadcast = false)
    public Views.RoomCatalogView catalog(@DestinationVariable String code, Principal player) {
        Room room = rooms.require(code);
        if (!room.isHost(player.getName())) {
            throw new GameException("NOT_HOST", "Seul l'hôte peut faire ça");
        }
        return ViewProjector.catalog(room.code(), room.setup(), factory.catalog());
    }

    /** L'hôte personnalise sa room (cartes, raretés, règles) ; renvoie le catalogue à jour. */
    @MessageMapping("/room/{code}/customize")
    @SendToUser(destinations = "/queue/catalog", broadcast = false)
    public Views.RoomCatalogView customize(@DestinationVariable String code, Requests.Customize req,
                                           Principal player) {
        Room room = rooms.require(code);
        if (!room.isHost(player.getName())) {
            throw new GameException("NOT_HOST", "Seul l'hôte peut faire ça");
        }
        var overrides = new RoomOverrides(req.excludedCards(), req.rarities(), req.rules());
        room.customize(player.getName(), factory.customized(room.preset().id(), overrides));
        return ViewProjector.catalog(room.code(), room.setup(), factory.catalog());
    }

    @MessageMapping("/room/{code}/vote")
    public void vote(@DestinationVariable String code, Requests.CastVote req, Principal player) {
        rooms.require(code).vote(player.getName(), req.teamOwnerId());
    }

    private Preset preset(String presetId) {
        return content.preset(presetId)
                .orElseThrow(() -> new GameException("UNKNOWN_PRESET", "Preset inconnu : " + presetId));
    }

    @MessageExceptionHandler(GameException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public Views.ErrorMessage onGameError(GameException e) {
        return Views.ErrorMessage.of(e.code(), e.getMessage());
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public Views.ErrorMessage onUnexpected(Exception e) {
        LOG.warn("Message STOMP rejeté", e);
        return Views.ErrorMessage.of("BAD_REQUEST", "Requête invalide");
    }
}
