package me.blindle.realtime.stomp;

import me.blindle.core.model.PlayerState;
import me.blindle.realtime.room.Broadcaster;
import me.blindle.realtime.room.RoomSnapshot;
import me.blindle.realtime.view.ViewProjector;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Diffusion STOMP : vue publique sur le topic de la room, vue privée par joueur. */
@Component
public class StompBroadcaster implements Broadcaster {

    public static final String ROOM_TOPIC = "/topic/room/";
    public static final String PLAYER_QUEUE = "/queue/room";

    private final SimpMessagingTemplate template;

    public StompBroadcaster(SimpMessagingTemplate template) {
        this.template = template;
    }

    @Override
    public void publish(RoomSnapshot snapshot) {
        template.convertAndSend(ROOM_TOPIC + snapshot.code(), ViewProjector.room(snapshot));
        for (PlayerState p : snapshot.state().players()) {
            template.convertAndSendToUser(p.id(), PLAYER_QUEUE, ViewProjector.player(snapshot, p.id()));
        }
    }

    @Override
    public void sendTo(RoomSnapshot snapshot, String playerId) {
        template.convertAndSendToUser(playerId, PLAYER_QUEUE, ViewProjector.player(snapshot, playerId));
        template.convertAndSendToUser(playerId, PLAYER_QUEUE, ViewProjector.room(snapshot));
    }
}
