package me.blindle.realtime.stomp;

import me.blindle.realtime.room.RoomRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Fin d'une session WebSocket : le joueur (identité attribuée à la poignée de main) est signalé
 * déconnecté à ses rooms, qui passent l'hôte au joueur suivant si besoin.
 */
@Component
public class DisconnectListener {

    private final RoomRegistry rooms;

    public DisconnectListener(RoomRegistry rooms) {
        this.rooms = rooms;
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        if (event.getUser() != null) {
            rooms.disconnect(event.getUser().getName());
        }
    }
}
