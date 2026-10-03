package guessmarket.client.api;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Runs pull-refresh tasks periodically. A task is skipped while its previous request is still in flight,
 * so a slow server never piles up requests.
 */
public final class Poller {
    public static final long DEFAULT_INTERVAL_MILLIS = 500;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "gm-poller");
        thread.setDaemon(true);
        return thread;
    });

    public void every(long intervalMillis, Supplier<CompletableFuture<?>> task) {
        AtomicBoolean busy = new AtomicBoolean(false);
        scheduler.scheduleWithFixedDelay(() -> {
            if (!busy.compareAndSet(false, true)) {
                return;
            }
            try {
                CompletableFuture<?> future = task.get();
                if (future == null) {
                    busy.set(false);
                } else {
                    future.whenComplete((result, error) -> busy.set(false));
                }
            } catch (RuntimeException ex) {
                busy.set(false);
            }
        }, 0, intervalMillis, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
    }
}
