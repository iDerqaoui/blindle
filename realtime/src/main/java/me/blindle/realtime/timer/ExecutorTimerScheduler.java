package me.blindle.realtime.timer;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Implémentation par {@link ScheduledExecutorService}. */
@Component
public class ExecutorTimerScheduler implements TimerScheduler {

    private final Clock clock;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "blindle-timer");
        t.setDaemon(true);
        return t;
    });

    public ExecutorTimerScheduler(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Handle schedule(Instant at, Runnable task) {
        long delay = Math.max(0, Duration.between(clock.instant(), at).toMillis());
        ScheduledFuture<?> future = executor.schedule(task, delay, TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
