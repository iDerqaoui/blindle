package me.blindle.realtime.stomp;

import me.blindle.core.GameException;
import me.blindle.realtime.room.Room;
import me.blindle.realtime.room.RoomRegistry;
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

    public GameController(RoomRegistry rooms) {
        this.rooms = rooms;
    }

    @MessageMapping("/room.create")
    public void create(Requests.CreateRoom req, Principal player) {
        Room room = rooms.create(req.presetId());
        room.join(player.getName(), req.name());
    }

    @MessageMapping("/room.join")
    public void join(Requests.JoinRoom req, Principal player) {
        rooms.require(req.code()).join(player.getName(), req.name());
    }

    @MessageMapping("/room/{code}/sync")
    public void sync(@DestinationVariable String code, Principal player) {
        rooms.require(code).sync(player.getName());
    }

    @MessageMapping("/room/{code}/settings")
    public void settings(@DestinationVariable String code, Requests.UpdateSettings req, Principal player) {
        rooms.require(code).updateSettings(player.getName(), req.budget(), req.rounds(), req.bidTimerSec());
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

    @MessageMapping("/room/{code}/vote")
    public void vote(@DestinationVariable String code, Requests.CastVote req, Principal player) {
        rooms.require(code).vote(player.getName(), req.teamOwnerId());
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
