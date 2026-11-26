package me.blindle.realtime.room;

/** Diffuse l'état d'une room : vue publique à tous, vue privée à chaque joueur. */
public interface Broadcaster {

    void publish(RoomSnapshot snapshot);

    /** Renvoie les deux vues à un seul joueur (resynchronisation après connexion). */
    void sendTo(RoomSnapshot snapshot, String playerId);
}
