package me.blindle.realtime.timer;

import java.time.Instant;

/** Minuteries côté serveur : seule source de vérité du temps de jeu. */
public interface TimerScheduler {

    /** Exécute {@code task} à l'instant {@code at} (immédiatement s'il est passé). */
    Handle schedule(Instant at, Runnable task);

    @FunctionalInterface
    interface Handle {
        void cancel();
    }
}
